package com.algaworks.algashop.product.catalog.application.product.event;

import com.algaworks.algashop.product.catalog.application.IntegrationEvent;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// Contrato PUBLICO do fato "produto adicionado ao catalogo" - o que outros servicos
// recebem via Kafka. Nao e o evento de dominio serializado: e uma projecao deliberada
// (id + instante), versionavel de forma independente do modelo interno.
// No consumidor (ordering) este evento NAO esta no type.mapping de proposito: chega
// como ObjectNode e cai no @KafkaHandler default - o exemplo vivo de que evento
// desconhecido nao quebra consumidor antigo.
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductAddedIntegrationEvent implements IntegrationEvent {
    private UUID productId;
    private OffsetDateTime addedAt;

    @Override
    public String getAggregateId() {
        if (productId == null) {
            return null;
        }

        return productId.toString();
    }
}
