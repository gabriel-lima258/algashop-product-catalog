package com.algaworks.algashop.product.catalog.domain.product;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

// O estoque chegou a zero. Emitido na TRANSICAO, nao a cada saque com estoque baixo.
//
// Diferente dos cinco eventos da Fase 12, este nao passa pelo AbstractAggregateRoot: ele
// nasce no StockService e sai pelo DomainEventPublisher, porque o ajuste de estoque nao
// carrega nem salva o agregado
@Getter
@Builder
@ToString
public class ProductSoldOutEvent {
    private UUID productId;

    @Builder.Default
    private OffsetDateTime soldOutAt = OffsetDateTime.now();
}
