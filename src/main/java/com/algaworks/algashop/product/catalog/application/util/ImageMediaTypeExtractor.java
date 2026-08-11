package com.algaworks.algashop.product.catalog.application.util;

import org.apache.commons.io.FilenameUtils;
import org.springframework.http.MediaType;

public class ImageMediaTypeExtractor {

    // nomes sem extensão devolvem octet-stream em vez de estourar, deixando a
    // rejeição de tipo inválido a cargo de quem chama
    public static MediaType fromFileName(String fileName) {
        String extension = FilenameUtils.getExtension(fileName);

        if (extension == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
