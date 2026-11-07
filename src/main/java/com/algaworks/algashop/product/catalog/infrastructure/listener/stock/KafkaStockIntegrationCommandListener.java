package com.algaworks.algashop.product.catalog.infrastructure.listener.stock;

import com.algaworks.algashop.product.catalog.application.OutboundIntegrationReply;
import com.algaworks.algashop.product.catalog.application.stock.command.ReserveStockIntegrationCommand;
import com.algaworks.algashop.product.catalog.application.stock.management.StockManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.stock.management.StockReservationInput;
import com.algaworks.algashop.product.catalog.application.stock.management.StockReservationItemInput;
import com.algaworks.algashop.product.catalog.application.stock.query.StockReservationOutput;
import com.algaworks.algashop.product.catalog.application.stock.query.StockReservationQueryService;
import com.algaworks.algashop.product.catalog.application.stock.reply.StockReservationConfirmedIntegrationReply;
import com.algaworks.algashop.product.catalog.application.stock.reply.StockReservationRejectedIntegrationReply;
import com.algaworks.algashop.product.catalog.infrastructure.util.BeanValidationUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Executa o comando ReserveStock do orquestrador e responde com o reply AUTOMATICO do
// Spring Kafka: o metodo retorna a resposta, e o @SendTo sem destino publica o retorno no
// kafka_replyTopic do comando, copiando o kafka_correlationId. Funciona aqui porque o
// resultado (reserva confirmada ou rejeitada) ja existe quando o metodo termina - no
// billing ele so existe depois do pagamento, e o reply teve de ser manual (pending_reply).
@Component
@Slf4j
@RequiredArgsConstructor
@KafkaListener(
        id = "#{algaShopMessagingKafkaProperties.stockCommandsConsumerGroup}",
        concurrency = "3",
        topics = "#{algaShopMessagingKafkaProperties.stockCommandsTopicName}")
public class KafkaStockIntegrationCommandListener {

    private final StockManagementApplicationService stockManagementApplicationService;
    private final StockReservationQueryService stockReservationQueryService;
    private final BeanValidationUtil beanValidationUtil;

    @KafkaHandler
    @SendTo
    public OutboundIntegrationReply handle(
            @Payload @Valid ReserveStockIntegrationCommand command,
            @Header(value = KafkaHeaders.CORRELATION_ID, required = false) byte[] correlationId,
            @Header(value = KafkaHeaders.REPLY_TOPIC, required = false) byte[] replyTopic,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        requireHeader(correlationId, KafkaHeaders.CORRELATION_ID);
        requireHeader(replyTopic, KafkaHeaders.REPLY_TOPIC);

        // reserva um stock
        UUID reservationId = stockManagementApplicationService.reserveForOrder(toInput(command));
        // comando e consulta separados (CQS): o servico de escrita devolve so o id, e os
        // dados para a resposta vem do lado de leitura
        StockReservationOutput reservationOutput = stockReservationQueryService.findById(reservationId);

        beanValidationUtil.validate(reservationOutput);

        // o retorno vira o reply (@SendTo): confirmada ou rejeitada
        return this.toReply(reservationOutput);
    }

    @KafkaHandler(isDefault = true)
    public void handle(
            @Payload Object command,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.warn("Unsupported stock command: type={} key={} offset={}",
                command.getClass().getSimpleName(), messageKey, offset);
        throw new IllegalArgumentException(
                "Unsupported stock command type " + command.getClass().getSimpleName());
    }

    private void requireHeader(byte[] value, String name) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("Stock command without the " + name + " header");
        }
    }

    private OutboundIntegrationReply toReply(StockReservationOutput reservation) {
        if (reservation.isConfirmed()) {
            return StockReservationConfirmedIntegrationReply.builder()
                    .reservationId(reservation.getId())
                    .orderId(reservation.getOrderId())
                    .confirmedAt(reservation.getCreatedAt())
                    .build();
        }
        return StockReservationRejectedIntegrationReply.builder()
                .reservationId(reservation.getId())
                .orderId(reservation.getOrderId())
                .rejectedAt(reservation.getCreatedAt())
                .build();
    }

    private StockReservationInput toInput(ReserveStockIntegrationCommand command) {
        return StockReservationInput.builder()
                .orderId(command.getOrderId())
                .items(command.getItems().stream()
                        .map(item -> StockReservationItemInput.builder()
                                .productId(item.productId())
                                .quantity(item.quantity())
                                .build())
                        .toList())
                .build();
    }
}