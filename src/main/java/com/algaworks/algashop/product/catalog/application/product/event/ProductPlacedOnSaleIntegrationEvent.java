package com.algaworks.algashop.product.catalog.application.product.event;

import com.algaworks.algashop.product.catalog.application.IntegrationEvent;
import com.algaworks.algashop.product.catalog.domain.util.IdGenerator;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductPlacedOnSaleIntegrationEvent implements IntegrationEvent {
    private UUID idempotencyKey = IdGenerator.generateTimeBasedUUID();
    private UUID productId;
    private BigDecimal regularPrice;
    private BigDecimal salePrice;
    private OffsetDateTime placedOnSaleAt;

    @Override
    public String getAggregateId() {
        if (productId == null) {
            return null;
        }
        return productId.toString();
    }

    @Override
    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}