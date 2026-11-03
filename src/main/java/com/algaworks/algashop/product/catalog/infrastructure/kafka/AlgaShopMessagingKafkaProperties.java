package com.algaworks.algashop.product.catalog.infrastructure.kafka;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

// Nome do topico de eventos de produto como propriedade tipada e VALIDADA: o
// @NotBlank derruba o boot na hora se product-event-topic-name nao estiver definido -
// melhor falhar cedo e com mensagem clara do que descobrir em runtime. E a unica
// fonte de verdade do nome: o NewTopic e o publisher (KafkaConfig) leem daqui, e o
// consumidor (ordering) le a mesma propriedade do lado dele.
@Component
@Validated
@Data
@ConfigurationProperties("algashop.messaging.kafka")
public class AlgaShopMessagingKafkaProperties {

    @NotBlank
    private String productEventTopicName;
}
