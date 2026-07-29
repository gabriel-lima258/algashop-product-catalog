package com.algaworks.algashop.product.catalog.infrastructure.util;

import org.springframework.util.ResourceUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

// classe para leitura de arquivos
// Le um recurso do classpath (src/main/resources) como texto UTF-8.
// Envolve IOException/FileNotFoundException em RuntimeException de proposito:
// arquivo de recurso faltando e erro de empacotamento, nao situacao que o chamador trata.
public class AlgaShopResourceUtils {

    // try-with-resources fecha o stream mesmo se a leitura estourar
    public static String readContent(String resourceName) {
        try (var inputStream = ResourceUtils.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new RuntimeException(new FileNotFoundException(resourceName));
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}