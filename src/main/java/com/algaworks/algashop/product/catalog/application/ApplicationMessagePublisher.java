package com.algaworks.algashop.product.catalog.application;

// PORTA DE SAIDA: a aplicacao declara o que precisa ("mandar uma mensagem para fora") sem
// dizer como. A interface mora aqui, na application; a implementacao mora na
// infrastructure, e hoje ela e um embrulho do ApplicationEventPublisher do Spring
// (ver infrastructure/message/ApplicationMessagePublisherConfig).
//
// O ganho concreto: o CategoryManagementApplicationService nao importa nada de Spring
// para publicar, e o dia em que isso virar RabbitMQ ou Kafka, quem muda e o @Bean -
// nenhum service precisa ser tocado.
//
// Object e nao um tipo Message proprio: nao ha contrato de mensagem nesta altura, e
// inventar um envelope agora seria adivinhar o formato que o broker futuro vai exigir.
// Ver docs/01-arquitetura-design/eventos-e-listeners.md
public interface ApplicationMessagePublisher {
    void send(Object message);
}