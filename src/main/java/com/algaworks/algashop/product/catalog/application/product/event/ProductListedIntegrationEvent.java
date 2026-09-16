package com.algaworks.algashop.product.catalog.application.product.event;

import com.algaworks.algashop.product.catalog.application.IntegrationEvent;
import com.algaworks.algashop.product.catalog.domain.util.IdGenerator;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// Contrato publico do fato "produto voltou a vitrine". Projecao do ProductListedEvent
// de dominio: so id + instante - quem precisar do estado atual consulta o catalogo
// (event notification, nao event-carried state transfer).
// getAggregateId() devolve o productId como key do record: eventos do mesmo produto
// caem na mesma particao e chegam em ordem ao consumidor.
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductListedIntegrationEvent implements IntegrationEvent {
    // @Builder.Default: sem ele o Lombok IGNORA o inicializador no builder e um
    // .builder().build() sairia com idempotencyKey nulo - evento sem header de dedupe,
    // em silencio (o publisher so grava o header quando a chave nao e nula)
    @Builder.Default
    private UUID idempotencyKey = IdGenerator.generateTimeBasedUUID();
    private UUID productId;
    private OffsetDateTime listedAt;

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
