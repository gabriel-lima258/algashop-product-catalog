package com.algaworks.algashop.product.catalog.infrastructure.storage.s3;

import com.algaworks.algashop.product.catalog.application.storage.FileReference;
import com.algaworks.algashop.product.catalog.application.storage.StorageProvider;
import com.algaworks.algashop.product.catalog.infrastructure.storage.StorageProviderException;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Exception;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URL;

// classe de implementação aws s3 de storage interface

// So entra no contexto quando algashop.storage.provider for s3 (ou estiver ausente).
// Sem essa condicao o Spring acha dois StorageProvider - este e o fake - e nao sabe
// qual injetar em quem depende da interface
@Component
@ConditionalOnProperty(name = "algashop.storage.provider", havingValue = "s3", matchIfMissing = true)
@RequiredArgsConstructor
public class StorageProviderAwsS3Impl implements StorageProvider {

    private final StorageProviderAwsS3Properties properties;
    private final S3Template s3Template;

    @Override
    public boolean healthCheck() {
        try {
            return s3Template.bucketExists(properties.getBucketName());
        } catch (Exception e) {
            return false;
        }
    }

    // gera uma imagem pre signed, ou seja, a imagem nunca passa pelo backend, sempre vai direto para o bucket
    @Override
    @SneakyThrows
    public URL requestUploadUrl(FileReference fileReference) {
        String bucketName = properties.getBucketName();
        String key = fileReference.getFileName();

        // Guarda contra sobrescrita. Hoje ela nunca dispara: quem chama gera um UUID
        // novo a cada requisicao, entao a chave e sempre inedita - e o custo e uma ida
        // ao S3 em TODO pedido de upload. Vale se um dia o nome vier de fora; enquanto
        // vier de UUID, e latencia paga por uma condicao impossivel.
        if (fileExists(key)) {
            throw new StorageProviderException(String.format("Remote file %s already exists", key));
        }

        // acl public-read: o objeto fica legivel por qualquer um que saiba a URL, sem
        // assinatura. E o que permite devolver a URL da imagem no JSON da API.
        ObjectMetadata.Builder metadataBulder = ObjectMetadata.builder();

        if (fileReference.isAllowPublicRead()) {
            metadataBulder.acl("public-read");
        }

        // A assinatura amarra metodo, bucket, chave, content-type e prazo - e nada alem
        // disso. Em particular ela NAO limita tamanho: o contentLength do FileReference
        // e informativo, e quem pedir autorizacao declarando 1 KB pode enviar gigabytes.
        // Limitar de verdade exigiria POST com policy e content-length-range.
        try {
            return s3Template.createSignedPutURL(
                    bucketName,
                    key,
                    fileReference.getExpiresIn(),
                    metadataBulder.build(),
                    fileReference.getContentType().toString()
            );
        } catch (S3Exception e) {
            throw new StorageProviderException(String.format("Unknown error when tried to create presigned URL for file %s", key), e);
        }
    }

    @Override
    public void deleteFile(String remoteFileName) {
        if (!fileExists(remoteFileName)) {
            throw new StorageProviderException(String.format("Remote file %s was not found", remoteFileName));
        }

        try {
            s3Template.deleteObject(properties.getBucketName(), remoteFileName);
        } catch (S3Exception e) {
            throw new StorageProviderException(String.format("Unknown error when tried to remove file %s", remoteFileName), e);
        }
    }

    @Override
    public boolean fileExists(String remoteFileName) {
        return s3Template.objectExists(properties.getBucketName(), remoteFileName);
    }
}
