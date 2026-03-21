package com.algaworks.algashop.product.catalog.application.category.query;

import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;

import java.util.UUID;

public class CategoryDetailOutputTestDataBuilder {

    private CategoryDetailOutputTestDataBuilder() {}

    public static CategoryDetailOutput.CategoryDetailOutputBuilder aNotebookCategory() {
        return CategoryDetailOutput.builder()
                .id(UUID.randomUUID())
                .name("Notebook")
                .enabled(true);

    }

    public static CategoryDetailOutput.CategoryDetailOutputBuilder anElectronicCategory() {
        return CategoryDetailOutput.builder()
                .id(UUID.randomUUID())
                .name("Electronic")
                .enabled(true);
    }
}
