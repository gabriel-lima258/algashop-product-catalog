package com.algaworks.algashop.product.catalog.application.category.query;

import com.algaworks.algashop.product.catalog.application.util.SortablePageFilter;
import lombok.*;
import org.springframework.data.domain.Sort;

// Command object: o Spring binda a query string direto nestes campos
// (GET /api/v1/categories?enabled=true&name=note&size=20), sem precisar de @RequestParam.
// Herda page/size de PageFilter e sortByProperty/sortDirection de SortablePageFilter.
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CategoryFilter extends SortablePageFilter<CategoryFilter.SortType> {

    private String name;    // busca por regex, casa em qualquer parte do nome
    private Boolean enabled;

    // usa o que o cliente mandou; se veio vazio, cai no default
    @Override
    public CategoryFilter.SortType getSortByPropertyOrDefault() {
        return getSortByProperty() != null ? getSortByProperty() : CategoryFilter.SortType.NAME;
    }

    @Override
    public Sort.Direction getSortDirectionOrDefault() {
        return getSortDirection() != null ? getSortDirection() : Sort.Direction.ASC;
    }

    // enum em vez de String solta: o cliente so consegue ordenar pelos campos
    // listados aqui, entao nao da pra pedir sort por campo inexistente
    @Getter
    @RequiredArgsConstructor
    public enum SortType {
        NAME("name");

        private final String propertyName;
    }
}
