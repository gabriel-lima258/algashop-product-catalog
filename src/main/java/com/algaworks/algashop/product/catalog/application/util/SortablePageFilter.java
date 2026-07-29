package com.algaworks.algashop.product.catalog.application.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Sort;

// Extensão genérica de PageFilter que adiciona capacidade de ordenação.
// O tipo genérico <T> representa o enum de propriedades permitidas para ordenação,
// garantindo que cada filtro concreto defina suas próprias opções de sort válidas (type-safe).
// Hierarquia: PageFilter (page, size) → SortablePageFilter (sortByProperty, sortDirection) → filtro concreto (campos de busca)
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class SortablePageFilter<T> extends PageFilter {
    private T sortByProperty;
    private Sort.Direction sortDirection;

    // construtor do super class
    public SortablePageFilter(int size, int page) {
        super(size, page);
    }

    // Métodos abstratos que obrigam cada filtro concreto a definir valores padrão de ordenação,
    // evitando NullPointerException caso o cliente não informe parâmetros de sort.
    public abstract T getSortByPropertyOrDefault();
    public abstract Sort.Direction getSortDirectionOrDefault();
}
