package com.algaworks.algashop.product.catalog.application.product.event;

import com.algaworks.algashop.product.catalog.application.IntegrationEvent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Event-Carried State Transfer: alem de avisar QUE o preco mudou, o evento CARREGA o
// estado (precos antigo e novo) - o consumidor atualiza o que precisar sem voltar aqui
// para perguntar. Compare com Listed/Delisted, que sao notification: so o fato e o id.
//
// O "V2" esta no NOME do contrato de proposito: o numero da versao viaja no __TypeId__
// logico (ProductCatalog.ProductPriceChangedV2IntegrationEvent), entao uma futura V3
// pode conviver com esta no mesmo topico - consumidores antigos seguem lendo V2 e o
// desconhecido cai no handler default. Nao existe V1 publicada: o contrato ja nasceu na
// convencao de versionar, e versionar ANTES de precisar e o que torna a evolucao
// possivel depois.
//
// Os @NotNull sao o contrato validado: o publisher (BeanValidationUtil) barra o evento
// invalido antes do send, e o consumidor valida de novo com @Valid ao receber - cada
// lado se protege sozinho, sem confiar no outro. Nota honesta: exigir oldRegular/
// oldSalePrice torna o contrato rigido (um "primeiro preco" sem anterior seria
// rejeitado) - restricao aceita conscientemente enquanto todo changePrice tem anterior.

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceChangedV2IntegrationEvent implements IntegrationEvent {
    @NotNull
    private UUID productId;
    @NotNull
    private OffsetDateTime changedAt;
    @NotNull
    private BigDecimal oldRegularPrice;
    @NotNull
    private BigDecimal oldSalePrice;
    @NotNull
    private BigDecimal newRegularPrice;
    @NotNull
    private BigDecimal newSalePrice;

    @Override
    public String getAggregateId() {
        if (productId == null) {
            return null;
        }

        return productId.toString();
    }
}
