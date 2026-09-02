package com.algaworks.algashop.product.catalog.infrastructure.message;

import com.algaworks.algashop.product.catalog.application.LocalEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// O adaptador da porta LocalEventPublisher. Uma method reference basta porque a
// interface tem um metodo so - nao ha classe para escrever.
//
// ATENCAO ao alcance do que esta sendo entregue aqui: o ApplicationEventPublisher do
// Spring e IN-PROCESS. A "mensagem" nunca sai da JVM, nao e persistida e nao sobrevive a
// uma queda. Isso e suficiente enquanto publicador e consumidor sao o mesmo servico -
// que e exatamente o caso do CategoryUpdatedEvent.
//
// Mensageria de verdade existe, mas e outra porta: IntegrationEventPublisher, cujo
// adaptador publica no Kafka (ver infrastructure/kafka/KafkaConfig)
@Configuration
public class LocalEventPublisherConfig {

    @Bean
    public LocalEventPublisher localEventPublisherPublisher(
            ApplicationEventPublisher applicationEventPublisher
    ) {
        return applicationEventPublisher::publishEvent;
    }
}
