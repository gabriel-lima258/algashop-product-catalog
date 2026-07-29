package com.algaworks.algashop.product.catalog.application.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Base de todo filtro de listagem: garante que sempre exista pagina e tamanho.
// Os defaults evitam o pior caso - cliente que nao passa nada e recebe a colecao inteira.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageFilter {
    private int size = 15;
    private int page = 0;
}
