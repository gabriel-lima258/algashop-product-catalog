package com.algaworks.algashop.product.catalog.application.category.query;

import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;

import java.time.OffsetDateTime;
import java.util.UUID;

public class CategoryDetailOutputTestDataBuilder {

    private CategoryDetailOutputTestDataBuilder() {}

    // version e updatedAt nao sao opcionais para os testes, pelo mesmo motivo do
    // ProductDetailOutputTestDataBuilder: o CategoryController.findById monta o ETag com
    // getVersion() e o Last-Modified com getUpdatedAt().toInstant(). Sem updatedAt o
    // controller estoura NullPointerException e o contrato recebe 500 no lugar de 200.
    //
    // O builder de produto foi ajustado quando o cache entrou; este ficou para tras, e
    // so o contract test acusou. Vale notar o padrao: cabecalho HTTP montado a partir de
    // campo do DTO transforma campo opcional em campo obrigatorio, e nada no
    // compilador diz isso.
    public static CategoryDetailOutput.CategoryDetailOutputBuilder aNotebookCategory() {
        return CategoryDetailOutput.builder()
                .id(UUID.randomUUID())
                .name("Notebook")
                .enabled(true)
                .version(1L)
                .updatedAt(OffsetDateTime.now());
    }

    public static CategoryDetailOutput.CategoryDetailOutputBuilder anElectronicCategory() {
        return CategoryDetailOutput.builder()
                .id(UUID.randomUUID())
                .name("Electronic")
                .enabled(true)
                .version(1L)
                .updatedAt(OffsetDateTime.now());
    }
}
