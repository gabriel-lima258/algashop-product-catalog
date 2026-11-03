package com.algaworks.algashop.product.catalog.application.stock.event;

import com.algaworks.algashop.product.catalog.application.IntegrationEvent;

public interface StockIntegrationEventPublisher {
    void send(IntegrationEvent event);
}
