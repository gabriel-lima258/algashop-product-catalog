package com.algaworks.algashop.product.catalog.infrastructure.storage.s3;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// classe para verificar health de s3, caso falhe lance DEGRADED

// Acompanha o StorageProviderAwsS3Impl: depende dele diretamente, entao so pode existir
// quando o provider s3 estiver ativo
@Component("awsS3")
@ConditionalOnProperty(name = "algashop.storage.provider", havingValue = "s3", matchIfMissing = true)
@RequiredArgsConstructor
public class StorageProviderAwsS3HealthIndicator implements HealthIndicator {

    private final StorageProviderAwsS3Impl storageProviderAwsS3;

    @Override
    public @Nullable Health health() {
        if (storageProviderAwsS3.healthCheck()) {
            return Health.up().build();
        }

        return Health.status("DEGRADED").build();
    }
}
