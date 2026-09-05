package com.algaworks.algashop.product.catalog.infrastructure.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Set;

// Valida um objeto com Bean Validation (as anotacoes @NotNull etc. da propria classe)
// e lanca ConstraintViolationException se houver violacao. Usado pelo publisher de
// eventos de integracao (KafkaConfig): o evento e validado ANTES do send() - um evento
// com campo nulo e barrado no produtor, onde o erro tem contexto e stack trace, em vez
// de virar JSON invalido no topico e estourar em outro servico.
// Atencao ao sentido da condicao: valida-se lancando quando o conjunto de violacoes NAO
// esta vazio. A versao invertida (if isEmpty -> throw) compila, passa despercebida e faz
// o oposto do combinado: derruba todo evento valido e deixa o invalido passar.

@Component
@RequiredArgsConstructor
public class BeanValidationUtil {

    private final LocalValidatorFactoryBean validatorFactory;

    public void validate(Object object) {
        Validator validator = validatorFactory.getValidator();
        Set<ConstraintViolation<Object>> violations = validator.validate(object);

        if (!violations.isEmpty()) {
            // ConstraintViolationException ja e ValidationException e carrega as violacoes -
            // embrulhar em outra excecao so esconderia os campos violados da mensagem
            throw new ConstraintViolationException(violations);
        }
    }
}
