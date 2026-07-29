package com.algaworks.algashop.product.catalog.domain.product;

import java.util.UUID;

// Alvo da projecao de ProductRepository.findAllByEnabled.
// O nome dos componentes do record tem que bater com os campos que voltam do Mongo
// (id <- _id, name <- name), senao o Spring Data nao consegue materializar.
public record ProductNameProjection(UUID id, String name) {
}
