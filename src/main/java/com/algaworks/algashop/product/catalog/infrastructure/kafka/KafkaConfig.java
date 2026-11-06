package com.algaworks.algashop.product.catalog.infrastructure.kafka;

import com.algaworks.algashop.product.catalog.application.EventPublishingException;
import com.algaworks.algashop.product.catalog.application.OutboundIntegrationEvent;
import com.algaworks.algashop.product.catalog.application.product.event.ProductIntegrationEventPublisher;
import com.algaworks.algashop.product.catalog.domain.DomainException;
import com.algaworks.algashop.product.catalog.infrastructure.util.BeanValidationUtil;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;
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

    private static final String DLT_PREFIX = "product-catalog.dlt.";

    public static final String TYPE_ID_HEADER = "__TypeId__";
    public static final String IDEMPOTENCY_KEY_HEADER = "idempotency-key";

    private static final int TOPIC_PARTITIONS = 3;
    private static final int TOPIC_REPLICAS = 3;
    private static final long RETENTION_30_DAYS = Duration.ofDays(30).toMillis();

    @Bean
    public NewTopic productsEventTopic(AlgaShopMessagingKafkaProperties properties) {
        return TopicBuilder.name(properties.getProductEventTopicName())
                .partitions(TOPIC_PARTITIONS)
                .replicas(TOPIC_REPLICAS)
                .configs(Map.of("min.insync.replicas", "2"))
                .build();
    }


    @Bean
    public NewTopic stockCommandsTopic(AlgaShopMessagingKafkaProperties properties) {
        return TopicBuilder.name(properties.getStockCommandsTopicName())
                .partitions(TOPIC_PARTITIONS)
                .replicas(TOPIC_REPLICAS)
                .configs(Map.of("min.insync.replicas", "2"))
                .build();
    }

    // método para criar topics de dead letter no lado consumer, configuração padrão
    private NewTopic createDeadLetterTopic(String originalTopicName) {
        return TopicBuilder.name(DLT_PREFIX + originalTopicName)
                .partitions(TOPIC_PARTITIONS) // o mesmo do topico original
                .replicas(TOPIC_REPLICAS)
                .configs(Map.of(
                        "min.insync.replicas", "2",
                        "retention.ms", String.valueOf(RETENTION_30_DAYS)
                ))
                .build();
    }

    /**
     * Recoverer de Dead Letter Topic (DLT): é chamado pelo DefaultErrorHandler quando
     * um record esgota todas as retentativas (ou lança uma exceção não-retryable).
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (consumerRecord, exception) ->
                        new TopicPartition(DLT_PREFIX + consumerRecord.topic(),
                                consumerRecord.partition())
        );
        recoverer.setLogRecoveryRecord(true);
        recoverer.setFailIfSendResultIsError(false);
        return recoverer;
    }


    // ignora eventos em retry em business exception e costumiza os valores de retries
    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        long interval = 2000L; // 2 segundos
        double multiplier = 2; // multiplicador exponencial
        long maxRetries = 3L; // 3 RETENTATIVAS: com a entrega original sao 4 execucoes (2s/4s/8s)

        ExponentialBackOff exponentialBackOff = new ExponentialBackOff(interval, multiplier);
        exponentialBackOff.setMaxAttempts(maxRetries);
        exponentialBackOff.setMaxInterval(8_000L);

        DefaultErrorHandler defaultErrorHandler = new DefaultErrorHandler(recoverer, exponentialBackOff);

        // não realiza retentativas dessas exceptions e envia para o dead letter topic
        defaultErrorHandler.addNotRetryableExceptions(
                DomainException.class,
                ConstraintViolationException.class,
                DataIntegrityViolationException.class,
                DuplicateKeyException.class,
                IllegalArgumentException.class
        );

        return defaultErrorHandler;
    }

    // a implementacao da porta de saida so transporta: topico e key nao sao decisao
    // do chamador - vem das properties e do proprio evento. Antes do send, o evento
    // passa pelo Bean Validation: contrato invalido morre no produtor, nao no topico
    @Bean
    public ProductIntegrationEventPublisher productIntegrationEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            AlgaShopMessagingKafkaProperties properties,
            BeanValidationUtil beanValidationUtil) {
        return event -> publish(event, properties.getProductEventTopicName(),
                kafkaTemplate, beanValidationUtil);
    }

    private void publish(
            OutboundIntegrationEvent event,
            String destination,
            KafkaTemplate<String, Object> kafkaTemplate,
            BeanValidationUtil beanValidationUtil) {
        beanValidationUtil.validate(event);
        SendResult<String, Object> result;
        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    destination,
                    event.getAggregateId(),
                    event);

            if (event.getIdempotencyKey() != null) {
                record.headers().add(KafkaConfig.IDEMPOTENCY_KEY_HEADER, event.getIdempotencyKey().toString().getBytes());
            }

            result = kafkaTemplate.send(record).get(40, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishingException("Interrupted while publishing", event, e);
        } catch (TimeoutException | ExecutionException | KafkaException e) {
            throw new EventPublishingException("Failed to publish", event, e);
        }

        RecordMetadata metadata = result.getRecordMetadata();

        log.info("Published {} to {}-{} at offset {}",
                event.getClass().getSimpleName(),
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
        );
    }


}
