package com.algaworks.algashop.product.catalog.application.product.query;

import com.algaworks.algashop.product.catalog.application.util.Slugfier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

// A categoria como ela aparece dentro de cada item da listagem. Espelha o ProductCategory
// gravado no documento - antes da desnormalizacao, os mesmos campos vinham do $lookup.
//
// O enabled entrou nesta etapa: o contrato em docs/openapi/product-catalog.yml ja o
// declarava como required havia tempo, e era a implementacao que estava atrasada
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryMinimalOutput implements Serializable {
    private UUID id;
    private String name;
    private Boolean enabled;

    public String getSlug() {
        return Slugfier.slugify(this.getName());
    }
}
