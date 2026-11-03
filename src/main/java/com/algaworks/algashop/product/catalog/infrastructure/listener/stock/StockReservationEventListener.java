package com.algaworks.algashop.product.catalog.infrastructure.listener.stock;

import com.algaworks.algashop.product.catalog.application.stock.event.StockIntegrationEventPublisher;
import com.algaworks.algashop.product.catalog.application.stock.event.StockReservationConfirmedIntegrationEvent;
import com.algaworks.algashop.product.catalog.application.stock.event.StockReservationRejectedIntegrationEvent;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.domain.stock.StockReservationConfirmedEvent;
import com.algaworks.algashop.product.catalog.domain.stock.StockReservationRejectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockReservationEventListener {

    private final StockIntegrationEventPublisher integrationEventPublisher;
    private final Mapper mapper;

    @TransactionalEventListener
    public void handle(StockReservationConfirmedEvent event) {
        log.info("StockReservationConfirmedEvent {}", event);
        var integrationEvent = mapper.convert(event, StockReservationConfirmedIntegrationEvent.class);
        integrationEventPublisher.send(integrationEvent);
    }

    @TransactionalEventListener
    public void handle(StockReservationRejectedEvent event) {
        log.info("StockReservationRejectedEvent {}", event);
        var integrationEvent = mapper.convert(event, StockReservationRejectedIntegrationEvent.class);
        integrationEventPublisher.send(integrationEvent);
    }
}
