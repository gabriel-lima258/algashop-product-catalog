package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.DomainEntityNotFoundException;

import java.util.UUID;

public class ProductNotFoundException extends DomainEntityNotFoundException {
    public ProductNotFoundException(UUID productId) {
        super(String.format("Product with id %s was not found", productId));
    }
}
