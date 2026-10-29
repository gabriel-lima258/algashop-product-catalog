package com.algaworks.algashop.product.catalog.domain.stock;

import lombok.*;

import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservationItem {

    private UUID productId;
    private Integer quantity;

    @Builder
    public StockReservationItem(UUID productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;

        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity cannot be less than 1");
        }
    }
}
