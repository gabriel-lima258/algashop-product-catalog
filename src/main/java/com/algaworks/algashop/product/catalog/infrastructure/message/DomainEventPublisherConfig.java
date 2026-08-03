package com.algaworks.algashop.product.catalog.infrastructure.message;

import com.algaworks.algashop.product.catalog.domain.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Adaptador da porta DomainEventPublisher - gemeo do ApplicationMessagePublisherConfig,
// ao lado. Os dois embrulham o MESMO ApplicationEventPublisher do Spring; o que os separa
// e a camada que cada porta serve.
//
// Vale o mesmo aviso do outro: o ApplicationEventPublisher e IN-PROCESS. O evento nao sai
// da JVM, nao e persistido e nao sobrevive a uma queda. Aqui isso pesa um pouco mais que
// na categoria, porque perder um ProductSoldOutEvent significa que ninguem foi avisado de
// que o produto acabou - e nada vai reparar depois
@Configuration
public class DomainEventPublisherConfig {

    @Bean
    public DomainEventPublisher domainEventPublisher(
            ApplicationEventPublisher applicationEventPublisher
    ) {
        return applicationEventPublisher::publishEvent;
    }
}
