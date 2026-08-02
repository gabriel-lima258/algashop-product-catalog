package com.algaworks.algashop.product.catalog.domain.product;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

// Produto que estava fora da vitrine voltou a aparecer. Par do ProductDelistedEvent;
// os dois saem do setEnabled, e so quando a situacao realmente muda
@Getter
@Builder
@ToString
public class ProductListedEvent {

    private UUID productId;

    @Builder.Default
    private OffsetDateTime listedAt = OffsetDateTime.now();
}
