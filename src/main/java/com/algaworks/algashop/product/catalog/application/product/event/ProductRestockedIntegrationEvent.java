package com.algaworks.algashop.product.catalog.application.product.event;

import com.algaworks.algashop.product.catalog.application.OutboundIntegrationEvent;
import com.algaworks.algashop.product.catalog.domain.util.IdGenerator;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductRestockedIntegrationEvent implements OutboundIntegrationEvent {
    private UUID idempotencyKey = IdGenerator.generateTimeBasedUUID();
    private UUID productId;
    private OffsetDateTime restockedAt;

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
