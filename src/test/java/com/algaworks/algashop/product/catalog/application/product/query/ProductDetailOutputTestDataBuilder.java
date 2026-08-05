package com.algaworks.algashop.product.catalog.application.product.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ProductDetailOutputTestDataBuilder {

    private ProductDetailOutputTestDataBuilder() {}

    // version e updatedAt nao sao opcionais para os testes: o ProductController.findById
    // monta ETag com getVersion() e Last-Modified com getUpdatedAt().toInstant() —
    // sem updatedAt o controller estoura NullPointerException antes de responder.
    public static ProductDetailOutput.ProductDetailOutputBuilder aProduct() {
        return ProductDetailOutput.builder()
                .id(UUID.randomUUID())
                .addedAt(OffsetDateTime.now())
                .version(1L)
                .updatedAt(OffsetDateTime.now())
                .name("Notebook X11")
                .brand("Deep Diver")
                .description("A Gamer Notebook")
                .regularPrice(new BigDecimal("1500.00"))
                .salePrice(new BigDecimal("1000.00"))
                .inStock(true)
                .enabled(true)
                .category(CategoryMinimalOutput.builder()
                        .id(UUID.randomUUID())
                        .name("Notebook")
                        .build());
    }

    public static ProductDetailOutput.ProductDetailOutputBuilder aProductAlt() {
        return ProductDetailOutput.builder()
                        .id(UUID.randomUUID())
                        .addedAt(OffsetDateTime.now())
                        .version(1L)
                        .updatedAt(OffsetDateTime.now())
                        .name("Desktop X900")
                        .brand("Deep Diver")
                        .description("A Gamer Desktop")
                        .regularPrice(new BigDecimal("1500.00"))
                        .salePrice(new BigDecimal("1000.00"))
                        // false de proposito: o contrato findProductsV1 lista um produto
                        // em estoque e outro fora, entao a asserção de inStock exercita
                        // os dois valores em vez de sempre o mesmo
                        .inStock(false)
                        .enabled(true)
                        .category(CategoryMinimalOutput.builder()
                                .id(UUID.randomUUID())
                                .name("Desktop")
                                .build());
    }
}
