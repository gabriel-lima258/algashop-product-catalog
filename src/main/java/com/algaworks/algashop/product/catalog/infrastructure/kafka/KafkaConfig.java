package com.algaworks.algashop.product.catalog.infrastructure.kafka;

import com.algaworks.algashop.product.catalog.application.EventPublishingException;
import com.algaworks.algashop.product.catalog.application.IntegrationEvent;
import com.algaworks.algashop.product.catalog.application.product.event.ProductIntegrationEventPublisher;
import com.algaworks.algashop.product.catalog.infrastructure.util.BeanValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
 *
 * Desde a Fase 39 o publisher valida o evento com Bean Validation (BeanValidationUtil)
 * antes do send(): evento com campo obrigatório nulo é barrado aqui, no produtor —
 * onde o erro tem contexto — em vez de atravessar o tópico e falhar no consumidor.
 *
 * result = kafkaTemplate.send(...).get(40, TimeUnit.SECONDS);
 *
 * O get() síncrono é a virada da Fase 41: a falha de publicação acontece NA THREAD
 * transacional — EventPublishingException sobe, o MongoTransactionManager aborta a
 * sessão e o save não commita. Falha no Kafka = rollback no banco = 503 na borda.
 *
 * A cadeia de timeouts, de dentro para fora: max.block.ms 5s (send() sem future) →
 * delivery.timeout.ms 30s (teto do producer: envio + retries + ack) → get(40s), que é
 * DELIBERADAMENTE MAIOR — rede de segurança caso o future não resolva; na prática o
 * producer desiste aos 30s e o get retorna antes → response-timeout do gateway 60s,
 * maior que tudo, para o rollback acontecer antes do cliente receber timeout.
 *
 * Existe uma chance de a mensagem ser entregue mesmo com a requisição falhando (o ack
 * que se perde no timeout): é a janela INVERSA do dual-write, aceita conscientemente —
 * melhor que o cliente receber 504 com a transação do MongoDB ainda aberta.
 */
@Slf4j
@Configuration
public class KafkaConfig {

    public static final String IDEMPOTENCY_KEY_HEADER = "idempotency-key";

    @Bean
    public NewTopic productsEventTopic(AlgaShopMessagingKafkaProperties properties) {
        return TopicBuilder.name(properties.getProductEventTopicName())
                .partitions(3)
                .replicas(3)
                .configs(Map.of("min.insync.replicas", "2"))
                .build();
    }

    // a implementacao da porta de saida so transporta: topico e key nao sao decisao
    // do chamador - vem das properties e do proprio evento. Antes do send, o evento
    // passa pelo Bean Validation: contrato invalido morre no produtor, nao no topico
    @Bean
    public ProductIntegrationEventPublisher productIntegrationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                                                             AlgaShopMessagingKafkaProperties properties,
                                                                             BeanValidationUtil beanValidationUtil) {
        return event -> {
            beanValidationUtil.validate(event);
            SendResult<String, Object> result = null;
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        properties.getProductEventTopicName(),
                        event.getAggregateId(),
                        event);

                if (event.getIdempotencyKey() != null) {
                    // publica a chave de idempotencia no header
                    record.headers().add(KafkaConfig.IDEMPOTENCY_KEY_HEADER, event.getIdempotencyKey().toString().getBytes());
                }

                result = kafkaTemplate.send(record).get(40, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EventPublishingException("Interrupted while publishing", event, e);
            } catch (TimeoutException | ExecutionException | KafkaException |
                     org.apache.kafka.common.KafkaException e) {
                // as DUAS KafkaException: a do Spring (falhas do template) e a do Apache -
                // sem a segunda, uma SerializationException (lancada sincrona no send)
                // escaparia do catch e viraria 500 generico em vez de 503 com rollback
                throw new EventPublishingException("Failed to publish", event, e);
            }

            RecordMetadata metadata = result.getRecordMetadata();

            log.info("Published {} to {}-{} at offset {}",
                    event.getClass().getSimpleName(),
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset()
            );
        };
    }
}
