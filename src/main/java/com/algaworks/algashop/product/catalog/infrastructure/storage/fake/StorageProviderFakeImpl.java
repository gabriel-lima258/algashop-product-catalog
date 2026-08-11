package com.algaworks.algashop.product.catalog.infrastructure.storage.fake;

import com.algaworks.algashop.product.catalog.application.storage.FileReference;
import com.algaworks.algashop.product.catalog.application.storage.StorageProvider;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.util.UUID;

// classe de implementação fake de storage interface

// Alternativa ao S3 para rodar sem localstack/AWS: basta algashop.storage.provider: fake
@Component
@ConditionalOnProperty(name = "algashop.storage.provider", havingValue = "fake")
public class StorageProviderFakeImpl implements StorageProvider {

    @Override
    public boolean healthCheck() {
        return true;
    }

    @Override
    @SneakyThrows
    public URL requestUploadUrl(FileReference fileReference) {
        return URI.create(String.format("http://localhost:4566/%s?token=%s",
                fileReference.getFileName(), UUID.randomUUID())).toURL();
    }

    @Override
    public void deleteFile(String remoteFileName) {

    }

    @Override
    public boolean fileExists(String remoteFileName) {
        return !remoteFileName.equals("fail.jpg");
    }
}
