package com.algaworks.algashop.product.catalog.infrastructure.listener.stock;

import com.algaworks.algashop.product.catalog.domain.DomainEventPublisher;
import com.algaworks.algashop.product.catalog.domain.product.ProductRestockedEvent;
import com.algaworks.algashop.product.catalog.domain.product.ProductSoldOutEvent;
import com.algaworks.algashop.product.catalog.domain.stock.StockMovementRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// O listener so traduz: o movimento registrado vira ProductSoldOut/ProductRestocked
// quando houve transicao. A regra da transicao e testada no StockMovementRegisteredEventTest
@ExtendWith(MockitoExtension.class)
class StockMovementEventListenerTest {

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private StockMovementEventListener listener;

    @Test
    void shouldPublishSoldOutWhenStockReachesZero() {
        listener.handle(movement(10, 0));

        verify(domainEventPublisher).publish(any(ProductSoldOutEvent.class));
        verify(domainEventPublisher, never()).publish(any(ProductRestockedEvent.class));
    }

    @Test
    void shouldPublishRestockedWhenStockLeavesZero() {
        listener.handle(movement(0, 10));

        verify(domainEventPublisher).publish(any(ProductRestockedEvent.class));
        verify(domainEventPublisher, never()).publish(any(ProductSoldOutEvent.class));
    }

    @Test
    void shouldPublishNothingWithoutTransition() {
        listener.handle(movement(50, 40));

        verify(domainEventPublisher, never()).publish(any());
    }

    private StockMovementRegisteredEvent movement(int previous, int current) {
        return StockMovementRegisteredEvent.builder()
                .productId(UUID.randomUUID())
                .previousQuantity(previous)
                .newQuantity(current)
                .build();
    }
}
