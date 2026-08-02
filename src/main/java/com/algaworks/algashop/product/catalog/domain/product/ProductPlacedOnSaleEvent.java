package com.algaworks.algashop.product.catalog.domain.product;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Emitido junto com o PriceChanged, e so quando o produto ENTRA em promocao - nao a cada
// alteracao de preco de um produto que ja estava promocionado.
// Dois eventos para uma unica mudanca de estado nao e redundancia: sao fatos de niveis
// diferentes. "o preco mudou" interessa a auditoria; "entrou em promocao" interessa a
// quem dispara notificacao, e o consumidor de um nao quer ser acordado pelo outro
@Getter
@Builder
@ToString
public class ProductPlacedOnSaleEvent {
    private UUID productId;
    private BigDecimal regularPrice;
    private BigDecimal salePrice;

    @Builder.Default
    private OffsetDateTime placedOnSaleAt = OffsetDateTime.now();
}
