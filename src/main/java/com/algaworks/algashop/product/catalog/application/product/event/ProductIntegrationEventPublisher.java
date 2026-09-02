package com.algaworks.algashop.product.catalog.application.product.event;


import com.algaworks.algashop.product.catalog.application.IntegrationEvent;

// Segunda porta de saida de eventos da aplicacao - a irma da LocalEventPublisher.
// A local entrega eventos de DOMINIO dentro do proprio processo; esta entrega eventos
// de INTEGRACAO para fora, via broker. Sao portas separadas de proposito: nem todo
// evento de dominio merece atravessar a fronteira do servico.
// A assinatura e enxuta de proposito: nao recebe destination nem key. O topico vem
// das properties (AlgaShopMessagingKafkaProperties) e a key vem do proprio evento
// (getAggregateId()) - o chamador diz O QUE aconteceu, nunca PARA ONDE vai.
// A implementacao vive em KafkaConfig, como bean anonimo sobre KafkaTemplate.
public interface ProductIntegrationEventPublisher {
    void send(IntegrationEvent event);
}
