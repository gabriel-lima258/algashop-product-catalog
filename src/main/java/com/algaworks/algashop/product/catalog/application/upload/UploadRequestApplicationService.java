package com.algaworks.algashop.product.catalog.application.upload;

import com.algaworks.algashop.product.catalog.application.storage.FileReference;
import com.algaworks.algashop.product.catalog.application.storage.StorageProvider;
import com.algaworks.algashop.product.catalog.application.util.ImageMediaTypeExtractor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

// Primeira das duas fases do upload. Aqui NAO passa arquivo nenhum - so o nome e o
// tamanho declarado. O servico responde com uma autorizacao, e o cliente envia o
// arquivo direto ao provedor:
//
//   1. POST /api/v1/upload-requests            -> uploadSignedUrl + remoteFileName
//   2. PUT  <uploadSignedUrl>                  -> o CLIENTE envia os bytes ao S3
//   3. POST /api/v1/products/{id}/images       -> o produto passa a conhecer a imagem
//
// O remoteFileName devolvido no passo 1 e o que amarra os tres: e ele que o cliente
// manda de volta no passo 3, e e por ele que o catalogo confere no storage se o
// arquivo realmente chegou.
@Service
@RequiredArgsConstructor
public class UploadRequestApplicationService {

    private final StorageProvider storageProvider;

    public UploadResponseOutput requestPreSignedUrl(UploadRequestInput input) {
        MediaType mediaType = ImageMediaTypeExtractor.fromFileName(input.getOriginalFileName());

        if (!(mediaType.equals(MediaType.IMAGE_JPEG) || mediaType.equals(MediaType.IMAGE_PNG))) {
            throw new IllegalArgumentException("Invalid image type");
        }

        String extension = FilenameUtils.getExtension(input.getOriginalFileName());

        // O nome do arquivo enviado pelo cliente e DESCARTADO: o que vai para o bucket
        // e um UUID novo. Isso resolve tres coisas de uma vez - colisao entre uploads,
        // path traversal em nome malicioso, e vazamento de informacao pelo nome original.
        //
        // allowPublicRead(true) fixo porque imagem de produto de catalogo e publica por
        // definicao; a URL de leitura vai no JSON da API sem nenhuma assinatura.
        //
        // 5 minutos e a janela em que a autorizacao vale. Passou disso, o cliente pede
        // outra - o preco de errar para menos e um retry, e para mais e uma permissao
        // de escrita circulando por tempo demais.
        FileReference fileReference = FileReference.builder()
                .contentLength(input.getContentLength())
                .contentType(mediaType)
                .fileName(UUID.randomUUID() + "." + extension)
                .expiresIn(Duration.ofMinutes(5))
                .allowPublicRead(true)
                .build();

        URL presignedUrl = storageProvider.requestUploadUrl(fileReference);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(fileReference.getExpiresIn());

        return UploadResponseOutput.builder()
                .uploadSignedUrl(presignedUrl.toString())
                .remoteFileName(fileReference.getFileName())
                .contentLength(fileReference.getContentLength())
                .contentType(fileReference.getContentType().toString())
                .expiresAt(expiresAt)
                .build();
    }
}
