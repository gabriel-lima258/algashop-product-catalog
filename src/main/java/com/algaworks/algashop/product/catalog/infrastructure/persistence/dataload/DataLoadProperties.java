package com.algaworks.algashop.product.catalog.infrastructure.persistence.dataload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

// Espelha o bloco algashop.data-load do application-base.yml:
//
//   algashop:
//     data-load:
//       enabled: true
//       auto-drop: true
//       sources:
//         - location: db/testdata/products.json
//           collection: products
//
// Repare no kebab-case do YAML virando camelCase no Java (auto-drop -> autoDrop).
// @Validated + @NotNull fazem a aplicacao NAO SUBIR se a config faltar - falha na
// inicializacao, com mensagem clara, em vez de NullPointerException la no meio da carga.
@Component
@ConfigurationProperties("algashop.data-load")
@Data
@Validated
public class DataLoadProperties {

    @NotNull
    private Boolean enabled;

    @NotNull
    private Boolean autoDrop;

    // @Valid propaga a validacao para dentro de cada item da lista
    @Valid
    private List<DataLoadSource> sources;

    // um par arquivo -> colecao de destino
    @Data
    public static class DataLoadSource {
        @NotBlank
        private String location;

        @NotBlank
        private String collection;
    }
}
