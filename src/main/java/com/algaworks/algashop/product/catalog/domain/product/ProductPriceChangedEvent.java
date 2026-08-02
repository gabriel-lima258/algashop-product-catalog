package com.algaworks.algashop.product.catalog.domain.product;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Leva o preco ANTIGO e o NOVO. E o unico evento do pacote que carrega estado anterior,
// porque e o unico cujo consumidor tipico precisa da diferenca, nao do valor final:
// avisar quem tem o produto na lista de desejos, montar historico de preco, medir
// margem. Reconstruir isso depois seria impossivel - o documento so guarda o agora
@Getter
@Builder
@ToString
public class ProductPriceChangedEvent {

    private UUID productId;
    private BigDecimal oldRegularPrice;
    private BigDecimal oldSalePrice;
    private BigDecimal newRegularPrice;
    private BigDecimal newSalePrice;

    @Builder.Default
    private OffsetDateTime changedAt = OffsetDateTime.now();
}
