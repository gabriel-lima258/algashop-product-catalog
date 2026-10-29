package com.algaworks.algashop.product.catalog.infrastructure.listener.stock;

import com.algaworks.algashop.product.catalog.domain.DomainEventPublisher;
import com.algaworks.algashop.product.catalog.domain.product.ProductRestockedEvent;
import com.algaworks.algashop.product.catalog.domain.product.ProductSoldOutEvent;
import com.algaworks.algashop.product.catalog.domain.stock.StockMovementRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockMovementEventListener {

    private final DomainEventPublisher domainEventPublisher;

    public void handle(StockMovementRegisteredEvent event) {
        log.info("StockMovementRegisteredEvent {}", event);

        // verifica qual evento enviar, caso esteja esgotado ou restocado
        if (event.causedSoldOut()) {
                domainEventPublisher.publish(ProductSoldOutEvent.builder()
                        .productId(event.getProductId())
                        .build());
        }

        if (event.causedRestock()) {
            domainEventPublisher.publish(ProductRestockedEvent.builder()
                    .productId(event.getProductId())
                    .build());
        }
    }
}
