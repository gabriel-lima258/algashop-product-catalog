package com.algaworks.algashop.product.catalog.infrastructure.listener.product;

import com.algaworks.algashop.product.catalog.application.product.event.ProductAddedIntegrationEvent;
import com.algaworks.algashop.product.catalog.application.product.event.ProductDelistedIntegrationEvent;
import com.algaworks.algashop.product.catalog.application.product.event.ProductIntegrationEventPublisher;
import com.algaworks.algashop.product.catalog.application.product.event.ProductListedIntegrationEvent;
import com.algaworks.algashop.product.catalog.application.product.event.ProductPriceChangedV2IntegrationEvent;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.domain.product.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// Consumidor dos eventos de dominio do Product. Todos registram em log - para tornar
// VISIVEL quando cada evento sai, que e a unica forma de perceber que eles so aparecem
// depois de um productRepository.save(). Alguns vao alem: ProductAddedEvent,
// ProductListedEvent, ProductDelistedEvent e ProductPriceChangedEvent sao convertidos
// (mapper) em eventos de integracao e publicados via ProductIntegrationEventPublisher,
// saindo no topico Kafka product-catalog.product.events. O de preco sai como V2
// (ProductPriceChangedV2IntegrationEvent) - versao no nome logico do contrato.
//
// Desde a Fase 41 TODOS os handlers sao SINCRONOS - o @Async que o handler de preco
// teve nas Fases 38-40 foi removido de proposito, e a mudanca e o coracao da fase:
//
//   sincrono -> roda NA MESMA THREAD e na mesma sessao transacional de quem salvou.
//               O publisher usa send().get() bloqueante; se o Kafka nao confirmar
//               (min.insync.replicas < 2, broker fora), a EventPublishingException
//               sobe, o MongoTransactionManager aborta a sessao e o save NAO commita.
//               Falha na publicacao = rollback no banco = 503 para o cliente.
//   @Async   -> a falha aconteceria em outra thread e viraria so log: o preco mudava
//               no banco e o evento nao saia - dual-write na forma mais silenciosa.
//
// Isso NAO e outbox: e "transacao estendida" (o commit espera o ack do broker). O
// preco e a latencia do request; o ganho e nunca commitar sem evento publicado. A
// janela inversa existe - ack perdido no timeout = evento entregue com transacao
// abortada - e foi aceita conscientemente (ver KafkaConfig). O ordering, com mais
// eventos e mais consumidores, foi de outbox de verdade nesta mesma fase.
//
// A assinatura ja diz o tipo, entao o @EventListener(X.class) e redundante - fica por
// legibilidade, deixando o filtro explicito na anotacao
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductEventListener {

    private final ProductIntegrationEventPublisher integrationEventPublisher;
    private final Mapper mapper;

    @TransactionalEventListener
    @Async
    public void handle(ProductAddedEvent event) {
        log.info("ProductAddedEvent: {}", event);
        var integrationEvent = mapper.convert(event, ProductAddedIntegrationEvent.class);
        integrationEventPublisher.send(integrationEvent);
    }

    @TransactionalEventListener
    @Async
    public void handle(ProductPriceChangedEvent event) {
        log.info("ProductPriceChangedEvent: {}", event);
        var integrationV2Event = mapper.convert(event, ProductPriceChangedV2IntegrationEvent.class);
        integrationEventPublisher.send(integrationV2Event);
    }

    @TransactionalEventListener
    @Async
    public void handle(ProductPlacedOnSaleEvent event) {
        log.info("ProductPlacedOnSaleEvent: {}", event);
//        integrationEventPublisher.send(event, event.getProductId().toString(), "product-catalog.product.events");
    }

    @TransactionalEventListener
    @Async
    public void handle(ProductListedEvent event) {
        log.info("ProductListedEvent: {}", event);
        var integrationEvent = mapper.convert(event, ProductListedIntegrationEvent.class);
        integrationEventPublisher.send(integrationEvent);
    }

    @TransactionalEventListener
    @Async
    public void handle(ProductDelistedEvent event) {
        log.info("ProductDelistedEvent: {}", event);
        var integrationEvent = mapper.convert(event, ProductDelistedIntegrationEvent.class);
        integrationEventPublisher.send(integrationEvent);
    }

    @TransactionalEventListener
    @Async
    public void handle(ProductRestockedEvent event) {
        log.info("ProductRestockedEvent: {}", event);
//        integrationEventPublisher.send(event, event.getProductId().toString(), "product-catalog.product.events");
    }

    @TransactionalEventListener
    public void handle(ProductSoldOutEvent event) {
        log.info("ProductSoldOutEvent: {}", event);
//        integrationEventPublisher.send(event, event.getProductId().toString(), "product-catalog.product.events");
    }
}
