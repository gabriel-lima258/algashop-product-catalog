package com.algaworks.algashop.product.catalog.infrastructure.listener.order;

import com.algaworks.algashop.product.catalog.application.order.event.OrderPaidIntegrationEvent;
import com.algaworks.algashop.product.catalog.application.stock.management.OrderPaidIntegrationEventAssembler;
import com.algaworks.algashop.product.catalog.application.stock.management.StockManagementApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@KafkaListener(
        id = "#{algaShopMessagingKafkaProperties.orderEventsConsumerGroup}",
        concurrency = "3",
        topics = "#{algaShopMessagingKafkaProperties.orderEventTopicName}")
public class KafkaOrderIntegrationEventListener {

    private final StockManagementApplicationService stockReservationApplicationService;
    private final OrderPaidIntegrationEventAssembler assembler;

    @KafkaHandler
    public void handle(
            @Payload @Valid OrderPaidIntegrationEvent event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        logReceived(event, messageKey, partition, offset);
        // converte o evento pelo assembler e chama o service de stock
        stockReservationApplicationService.reserveForOrder(
                assembler.toStockReservationInput(event)
        );
    }

    @KafkaHandler(isDefault = true)
    public void handle(
            @Payload Object event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset) {
        log.info("Event ignored: type={} key={} offset={}",
                event.getClass().getSimpleName(), messageKey, offset);
    }

    private void logReceived(Object event, String messageKey, Integer partition, Long offset) {
        log.info("Received {} | key={} | partiton={} | offset={} | thread={}",
                event.getClass().getSimpleName(), messageKey, partition, offset,
                Thread.currentThread().getName());
    }
}
