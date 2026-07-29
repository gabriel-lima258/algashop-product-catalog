package com.algaworks.algashop.product.catalog.application.product.query;

import com.algaworks.algashop.product.catalog.application.util.SortablePageFilter;
import lombok.*;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Command object: o Spring binda a query string direto nestes campos
// (GET /api/v1/products?enabled=true&priceFrom=100&size=20), sem precisar de @RequestParam.
// Todo campo e opcional - null significa "nao filtra por isso".
// Herda page/size de PageFilter e sortByProperty/sortDirection de SortablePageFilter.
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProductFilter extends SortablePageFilter<ProductFilter.SortType> {

    private String term;            // busca textual em name, brand e description
    private Boolean hasDiscount;    // salePrice menor que regularPrice
    private Boolean enabled;
    private Boolean inStock;        // quantityInStock maior que zero
    private BigDecimal priceFrom;   // faixa de salePrice; os dois extremos
    private BigDecimal priceTo;     // sao independentes (da pra mandar so um)
    private UUID[] categoriesId;    // vira $in - aceita varias categorias
    private OffsetDateTime addedAtFrom; // intervalo de cadastro; mesma regra
    private OffsetDateTime addedAtTo;   // dos precos - extremos independentes

    // ATENCAO: hoje devolvem constante e ignoram o que o cliente mandou -
    // faltou o fallback (sortByProperty != null ? sortByProperty : ADDED_AT)
    @Override
    public SortType getSortByPropertyOrDefault() {
        return SortType.ADDED_AT;
    }

    @Override
    public Sort.Direction getSortDirectionOrDefault() {
        return Sort.Direction.ASC;
    }

    // enum em vez de String solta: o cliente so consegue ordenar pelos campos
    // listados aqui, entao nao da pra pedir sort por campo inexistente
    @Getter
    @RequiredArgsConstructor
    public enum SortType {
        ADDED_AT("addedAt"),
        SALE_PRICE("salePrice");

        private final String propertyName;
    }
}
