package com.algaworks.algashop.product.catalog.domain.product;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

// Produto saiu da vitrine (disable). Nao e exclusao: o documento continua la, so deixa
// de ser listado - por isso "delisted" e nao "deleted".
// Par do ProductListedEvent
@Getter
@Builder
@ToString
public class ProductDelistedEvent {

    private UUID productId;

    @Builder.Default
    private OffsetDateTime deslistedAt = OffsetDateTime.now();
}
