package com.algaworks.algashop.product.catalog.application;

// Marcador dos comandos que RECEBE do orquestrador
// Espelha o OutboundIntegrationCommand do ordering; nao precisa de key: quem a escolheu
// foi o remetente, e ela chega pronta no header RECEIVED_KEY.
public interface InboundIntegrationCommand {
}
