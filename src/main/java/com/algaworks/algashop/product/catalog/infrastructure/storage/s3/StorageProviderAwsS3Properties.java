package com.algaworks.algashop.product.catalog.infrastructure.storage.s3;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

// Condicionada junto com o provider s3: com o fake ativo ninguem informa bucket-name e o
// @NotBlank derrubaria a aplicacao na subida
@Data
@Validated
@Configuration
@ConditionalOnProperty(name = "algashop.storage.provider", havingValue = "s3", matchIfMissing = true)
@ConfigurationProperties("algashop.storage.s3")
public class StorageProviderAwsS3Properties {

    @NotBlank
    private String bucketName;
}
