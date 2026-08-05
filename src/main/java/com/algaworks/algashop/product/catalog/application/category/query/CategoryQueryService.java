package com.algaworks.algashop.product.catalog.application.category.query;

import com.algaworks.algashop.product.catalog.application.util.CacheNames;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import org.springframework.cache.annotation.Cacheable;

import java.time.OffsetDateTime;
import java.util.UUID;

// As anotacoes de cache ficam na INTERFACE, e nao na implementacao. O Spring le a
// anotacao pelo tipo que o proxy expoe, e o proxy expoe a interface - entao funciona
// nos dois lugares. Aqui e melhor por uma razao de desenho: "esta consulta e
// cacheavel" e uma decisao sobre o contrato, nao sobre como o Mongo a resolve. Quem
// implementar outra vez, contra outro banco, herda a decisao junto.
public interface CategoryQueryService {

    // CACHE-ASIDE (lazy loading): o Spring olha o cache; se achar, devolve e o metodo
    // nem roda; se nao achar, roda, guarda o retorno e devolve. O banco so e tocado no
    // miss.
    //
    // A chave e a string fixa 'default', nao o filtro. Isso e deliberado e e o ponto
    // mais importante deste cache: cachear listagem com filtro livre e armadilha de
    // cardinalidade - cada combinacao de nome, pagina, tamanho e ordenacao vira uma
    // entrada, o Redis enche de chaves com um acesso cada, e a taxa de acerto tende a
    // zero enquanto a memoria vai embora. Por isso o condition: so o filtro default
    // entra no cache, que e o que a maioria esmagadora das visitas pede.
    @Cacheable(cacheNames = CacheNames.CATEGORIES_FILTER,
            key = "'default'",
            condition = "#filter.isCacheable()")
    PageModel<CategoryDetailOutput> filter(CategoryFilter filter);

    @Cacheable(cacheNames = CacheNames.CATEGORIES,
            key = "#categoryId")
    CategoryDetailOutput findById(UUID categoryId);

    // NAO e cacheado, de proposito: e a consulta que alimenta o Last-Modified do cache
    // do CLIENTE. Cachear o carimbo que decide se o cliente pode reusar a resposta
    // seria cachear o proprio criterio de invalidacao - o 304 continuaria sendo
    // devolvido depois de a listagem ja ter mudado.
    OffsetDateTime lastModified();
}
