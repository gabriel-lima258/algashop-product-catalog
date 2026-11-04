package com.algaworks.algashop.product.catalog.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

// Contrato dos eventos de integracao que o catalogo PUBLICA. O getAggregateId() vira a
// KEY do record - a key decide a particao, e a particao decide a ordem: todos os eventos
// de um mesmo produto saem na sequencia em que aconteceram (ordem POR AGREGADO).
// A key nasce aqui, no evento, e nao no publisher: quem sabe qual agregado originou
// o fato e o proprio evento - o publisher so transporta.
public interface OutboundIntegrationEvent {
    // @JsonIgnore tira o aggregateId do JSON publicado: ele ja viaja como KEY do record
    // e os eventos expoem o productId como campo - serializa-lo seria duplicar. E mudanca
    // de wire format: so foi segura porque os dois lados mudaram juntos, antes de haver
    // consumidor de fora. Um evento futuro cujo payload NAO exponha o id do agregado
    // precisaria repensar isso, ou o id sumiria do corpo da mensagem
    @JsonIgnore
    String getAggregateId();

    // gera uma chave para garantir a idempotencia de evento em seu envio, mas o restock não precisa por isso null como default
    @JsonIgnore
    default UUID getIdempotencyKey() {
        return null;
    }
}
