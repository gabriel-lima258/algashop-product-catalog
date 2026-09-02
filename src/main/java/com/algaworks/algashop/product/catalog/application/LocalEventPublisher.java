package com.algaworks.algashop.product.catalog.application;

// PORTA DE SAIDA para eventos LOCAIS: a aplicacao declara o que precisa ("avisar que algo
// aconteceu") sem dizer como. A interface mora aqui, na application; a implementacao mora
// na infrastructure, e e um embrulho do ApplicationEventPublisher do Spring
// (ver infrastructure/message/LocalEventPublisherConfig).
//
// "Local" e o ponto: a mensagem circula DENTRO da mesma JVM, entre componentes deste
// proprio servico (ex.: CategoryUpdatedEvent disparando a propagacao nos produtos).
// Ela nao e persistida e nao sobrevive a uma queda - e isso e aceitavel porque
// publicador e consumidor sao o mesmo processo.
//
// Quando o evento precisa CRUZAR a fronteira do servico e chegar a outros sistemas,
// a porta e outra: IntegrationEventPublisher, cujo adaptador publica no Kafka
// (ver infrastructure/kafka/KafkaConfig). As duas convivem de proposito - nem todo
// evento de dominio merece virar mensagem no broker.
//
// O ganho concreto continua o mesmo: o CategoryManagementApplicationService nao importa
// nada de Spring para publicar, e Object (e nao um tipo Message proprio) porque nao ha
// contrato de mensagem nesta altura.
// Ver docs/01-arquitetura-design/eventos-e-listeners.md
public interface LocalEventPublisher {
    void send(Object message);
}
