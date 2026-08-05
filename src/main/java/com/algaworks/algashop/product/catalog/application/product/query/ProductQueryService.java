package com.algaworks.algashop.product.catalog.application.product.query;

import com.algaworks.algashop.product.catalog.application.util.CacheNames;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import org.springframework.cache.annotation.Cacheable;

import java.util.UUID;

public interface ProductQueryService {

    // CACHE-ASIDE: le do cache, cai no Mongo so no miss, e guarda o resultado.
    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    ProductDetailOutput findById(UUID productId);

    // A listagem de produtos NAO e cacheada, ao contrario da de categorias. Os dois
    // casos parecem iguais e nao sao: o filtro de produto tem termo de busca, faixa de
    // preco, categoria e ordenacao, entao a cardinalidade de chaves e alta e a chance
    // de duas pessoas pedirem exatamente a mesma combinacao e baixa. Cache so paga
    // quando ha reuso; sem reuso, ele e memoria gasta para servir uma leitura so.
    PageModel<ProductSummaryOutput> filter(ProductFilter filter);
}
