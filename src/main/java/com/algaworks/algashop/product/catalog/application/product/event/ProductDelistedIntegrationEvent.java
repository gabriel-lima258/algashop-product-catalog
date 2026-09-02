package com.algaworks.algashop.product.catalog.application.product.event;

import com.algaworks.algashop.product.catalog.application.IntegrationEvent;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// Contrato publico do fato "produto saiu da vitrine" (disable - nao e exclusao).
// Par do ProductListedIntegrationEvent; projecao do ProductDelistedEvent de dominio.
// O campo aqui e delistedAt (correto), mas o evento de DOMINIO carrega o typo
// deslistedAt - o de-para explicito vive no ModelMapperConfig. Consertar foi barato
// agora; depois que o JSON circula, renomear campo e quebra de contrato.
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDelistedIntegrationEvent implements IntegrationEvent {
    private UUID productId;
    private OffsetDateTime delistedAt;

    @Override
    public String getAggregateId() {
        if (productId == null) {
            return null;
        }

        return productId.toString();
    }
}
