package com.algaworks.algashop.product.catalog.application.util;

// Os nomes dos caches, num lugar so.
//
// Antes eram tres literais repetidos em nove pontos - cinco anotacoes, duas
// configuracoes e dois evicts. O problema nao e a repeticao, e o tipo de erro que
// ela permite: um typo em @Cacheable("algashop:produtcs:v1") nao quebra nada. O
// Spring cria um cache novo com esse nome, ele cai no cacheDefaults em vez da
// configuracao especifica, e o @CacheEvict que aponta para o nome certo passa a
// apagar um cache que ninguem preenche. Tudo isso em silencio, com a aplicacao
// respondendo normalmente.
//
// Com constante, o mesmo typo nao compila.
//
// Por que em application/util e nao em infrastructure/cache, onde mora o
// RedisCacheConfig: quem MAIS usa esses nomes e a camada de aplicacao - as
// anotacoes estao nas interfaces de query e nos application services. Se a
// constante morasse na infraestrutura, a aplicacao passaria a depender dela, e a
// seta de dependencia aponta para o outro lado. O RedisCacheConfig importar de
// application e o sentido correto.
//
// O :v1 no fim e proposital. Serializacao JDK grava o objeto acoplado a assinatura
// da classe: acrescentar um campo a um DTO cacheado muda o serialVersionUID
// calculado, e as entradas antigas passam a estourar InvalidClassException na
// leitura. Subir para :v2 numa mudanca dessas troca o namespace inteiro de uma vez,
// e as chaves velhas morrem sozinhas no TTL - mais simples que limpar o Redis na
// hora do deploy.
public final class CacheNames {

    // Produto por id. Escrito por @CachePut (write-through) e lido por @Cacheable
    public static final String PRODUCTS = "algashop:products:v1";

    // Categoria por id
    public static final String CATEGORIES = "algashop:categories:v1";

    // A listagem de categorias - e SO a do filtro default, sob a chave fixa 'default'.
    // Ver CategoryFilter.isCacheable()
    public static final String CATEGORIES_FILTER = "algashop:categories-filter:v1";

    private CacheNames() {
    }
}
