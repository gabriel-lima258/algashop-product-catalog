package com.algaworks.algashop.product.catalog.domain;

// Porta de saida para eventos que nascem no DOMINIO mas nao passam por um save().
//
// O agregado normalmente nao precisa disto: Product estende AbstractAggregateRoot e o
// Spring Data publica o que ele enfileirou quando o ProductRepository salva. Acontece que
// o ajuste de estoque nao salva o agregado - ele altera o documento direto, para ser
// atomico -, entao aquele mecanismo simplesmente nao e acionado. Sem esta porta, o
// ProductSoldOutEvent nao teria por onde sair.
//
// ATENCAO: existe uma irma quase identica, a ApplicationMessagePublisher, na camada
// application. A duplicacao e proposital e a diferenca e de CAMADA, nao de comportamento:
// o dominio nao pode depender de uma interface declarada na application. Ambas sao
// implementadas pelo mesmo ApplicationEventPublisher do Spring, em @Configuration
// separados. Ver docs/01-arquitetura-design/eventos-e-listeners.md
public interface DomainEventPublisher {
    void publish(Object event);
}