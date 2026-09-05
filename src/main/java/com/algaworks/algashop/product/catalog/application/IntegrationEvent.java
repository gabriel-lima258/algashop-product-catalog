package com.algaworks.algashop.product.catalog.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

// Contrato minimo de todo evento de integracao do catalogo: expor o id do agregado
// que o originou. Esse id vira a KEY do record no Kafka - e a key decide a particao,
// e a particao decide a ordem: todos os eventos de um mesmo produto caem na mesma
// particao e sao consumidos na sequencia em que aconteceram; produtos diferentes se
// intercalam livremente (cronologia POR AGREGADO, nao global).
// A key nasce aqui, no evento, e nao no publisher: quem sabe qual agregado originou
// o fato e o proprio evento - o publisher so transporta.
public interface IntegrationEvent {
    // @JsonIgnore tira o aggregateId do JSON publicado: ele ja viaja como KEY do record
    // e os eventos expoem o productId como campo - serializa-lo seria duplicar. E mudanca
    // de wire format: so foi segura porque os dois lados mudaram juntos, antes de haver
    // consumidor de fora. Um evento futuro cujo payload NAO exponha o id do agregado
    // precisaria repensar isso, ou o id sumiria do corpo da mensagem
    @JsonIgnore
    String getAggregateId();
}
