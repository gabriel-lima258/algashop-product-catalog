package com.algaworks.algashop.product.catalog.application;

// Contrato minimo de todo evento de integracao do catalogo: expor o id do agregado
// que o originou. Esse id vira a KEY do record no Kafka - e a key decide a particao,
// e a particao decide a ordem: todos os eventos de um mesmo produto caem na mesma
// particao e sao consumidos na sequencia em que aconteceram; produtos diferentes se
// intercalam livremente (cronologia POR AGREGADO, nao global).
// A key nasce aqui, no evento, e nao no publisher: quem sabe qual agregado originou
// o fato e o proprio evento - o publisher so transporta.
public interface IntegrationEvent {
    String getAggregateId();
}
