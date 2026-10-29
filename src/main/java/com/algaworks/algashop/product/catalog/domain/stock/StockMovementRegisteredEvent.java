package com.algaworks.algashop.product.catalog.domain.stock;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@ToString
@Builder
public class StockMovementRegisteredEvent {

    private UUID movementId;
    private UUID productId;
    private StockMovement.MovementType type;
    private String orderId;
    private Integer movementQuantity;
    private Integer previousQuantity;
    private Integer newQuantity;

    @Builder.Default
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    public boolean causedSoldOut() {
        return newQuantity != null && previousQuantity != null
                && newQuantity == 0 && previousQuantity != 0;
    }

    public boolean causedRestock() {
        return newQuantity != null && previousQuantity != null
                && newQuantity > 0 && previousQuantity == 0;
    }
}
