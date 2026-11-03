package com.algaworks.algashop.product.catalog.application.stock.management;

import com.algaworks.algashop.product.catalog.application.order.event.OrderItemSnapshot;
import com.algaworks.algashop.product.catalog.application.order.event.OrderPaidIntegrationEvent;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Objects;

@Component
public class OrderPaidIntegrationEventAssembler {

    public StockReservationInput toStockReservationInput(OrderPaidIntegrationEvent event) {
        return StockReservationInput.builder()
                .orderId(event.getOrderId())
                .items(event.getItems().stream()
                        .map(this::toStockReservationItemInput)
                        .toList())
                .build();
    }

    private StockReservationItemInput toStockReservationItemInput(OrderItemSnapshot item) {
        return StockReservationItemInput.builder()
                .productId(item.productId())
                .quantity(item.quantity())
                .build();
    }

}
