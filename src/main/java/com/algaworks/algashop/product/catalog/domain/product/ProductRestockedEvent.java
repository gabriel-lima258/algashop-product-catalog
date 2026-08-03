package com.algaworks.algashop.product.catalog.domain.product;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

// O produto VOLTOU a ter estoque, saindo do zero. Par do ProductSoldOutEvent, e com a
// mesma regra: reposicao sobre estoque que ja era positivo nao emite nada.
//
// Como o SoldOut, sai pelo DomainEventPublisher e nao pelo AbstractAggregateRoot
@Getter
@Builder
@ToString
public class ProductRestockedEvent {
    private UUID productId;

    @Builder.Default
    private OffsetDateTime restockedAt = OffsetDateTime.now();
}
