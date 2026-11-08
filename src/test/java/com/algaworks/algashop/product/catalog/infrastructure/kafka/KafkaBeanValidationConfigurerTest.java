package com.algaworks.algashop.product.catalog.infrastructure.kafka;

import com.algaworks.algashop.product.catalog.application.stock.command.ReserveStockIntegrationCommand;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

// Sem Spring, Kafka ou banco: prova que o configurer registra o Validator no registrar e
// que esse Validator enxerga as constraints do comando de reserva de estoque
class KafkaBeanValidationConfigurerTest {

    private static final String ORDER_ID = "01K00000000000000000000000";

    private LocalValidatorFactoryBean validator;
    private KafkaListenerEndpointRegistrar registrar;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        registrar = new KafkaListenerEndpointRegistrar();
        new KafkaBeanValidationConfigurer(validator).configureKafkaListeners(registrar);
    }

    @Test
    void shouldRegisterTheValidatorItReceived() {
        Assertions.assertThat(registrar.getValidator()).isSameAs(validator);
    }

    @Test
    void shouldPointTheViolationOfACommandWithoutItems() {
        Errors errors = validate(ReserveStockIntegrationCommand.builder()
                .orderId(ORDER_ID)
                .items(List.of())
                .build());

        Assertions.assertThat(errors.hasErrors()).isTrue();
        Assertions.assertThat(errors.getFieldError("items")).isNotNull();
    }

    @Test
    void shouldAcceptAValidCommand() {
        Errors errors = validate(ReserveStockIntegrationCommand.builder()
                .orderId(ORDER_ID)
                .items(List.of(new ReserveStockIntegrationCommand.Item(UUID.randomUUID(), 2)))
                .build());

        Assertions.assertThat(errors.hasErrors()).isFalse();
    }

    private Errors validate(Object payload) {
        Errors errors = new BeanPropertyBindingResult(payload, "payload");
        registrar.getValidator().validate(payload, errors);
        return errors;
    }
}
