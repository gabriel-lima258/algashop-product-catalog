package com.algaworks.algashop.product.catalog.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

// Contrato das respostas (replies) que o product catalog envia ao orquestrador. Diferente do
// evento, a resposta tem destinatario: vai para o topico que o comando indicou. O
// getAggregateId() vira a KEY do record - o orderId, a mesma key do comando que ela
// responde, para comando e resposta do mesmo pedido ficarem na mesma particao.
// Atencao: com o reply automatico (@SendTo) e o framework que monta o record, e ninguem
// chama getAggregateId() - a key do reply e a que o Spring Kafka definir. Conferir na GUI.
public interface OutboundIntegrationReply {
    @JsonIgnore
    String getAggregateId();
}
