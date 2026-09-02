package com.algaworks.algashop.product.catalog.infrastructure.kafka;

import com.algaworks.algashop.product.catalog.application.IntegrationEvent;
import com.algaworks.algashop.product.catalog.application.product.event.ProductIntegrationEventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/**
 * Configuração de infraestrutura do Kafka para o serviço de catálogo de produtos.
 *
 * Declara o tópico de eventos de produto — nome vindo de
 * AlgaShopMessagingKafkaProperties, a mesma fonte que o publisher usa, para que o
 * tópico declarado e o tópico usado nunca divirjam — provisionado automaticamente
 * pelo Spring Kafka na inicialização da aplicação com 3 partições e 3 réplicas,
 * exigindo no mínimo 2 réplicas sincronizadas (min.insync.replicas) para aceitar
 * gravações — garantindo durabilidade dos eventos mesmo com a queda de um broker.
 *
 * Também implementa a porta ProductIntegrationEventPublisher como bean anônimo sobre
 * o KafkaTemplate: destino = tópico das properties, key = getAggregateId() do evento
 * (cronologia por agregado: mesma key, mesma partição, mesma ordem) e value
 * serializado em JSON pelo JacksonJsonSerializer, que grava no header __TypeId__ o
 * nome LÓGICO do tipo (spring.json.type.mapping) — é esse nome, e não o nome da
 * classe Java, que o consumidor usa para desserializar, desacoplando os pacotes do
 * produtor e do consumidor.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic productsEventTopic(AlgaShopMessagingKafkaProperties properties) {
        return TopicBuilder.name(properties.getProductEventTopicName())
                .partitions(3)
                .replicas(3)
                .configs(Map.of("min.insync.replicas", "2"))
                .build();
    }

    // a implementacao da porta de saida so transporta: topico e key nao sao decisao
    // do chamador - vem das properties e do proprio evento
    @Bean
    public ProductIntegrationEventPublisher productIntegrationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                                                             AlgaShopMessagingKafkaProperties properties) {
        return new ProductIntegrationEventPublisher() {
            @Override
            public void send(IntegrationEvent event) {
                kafkaTemplate.send(properties.getProductEventTopicName(), event.getAggregateId(), event);
            }
        };
    }
}
