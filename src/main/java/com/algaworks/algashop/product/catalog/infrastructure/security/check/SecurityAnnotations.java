package com.algaworks.algashop.product.catalog.infrastructure.security.check;


import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Meta-anotacoes de escopo. Cada uma embrulha um @PreAuthorize para que o controller
// declare a INTENCAO ("quem le pedido") em vez da expressao ("hasAuthority('SCOPE_...')").
//
// Tres razoes para isso nao ser so acucar sintatico:
//
// 1. A expressao do @PreAuthorize e uma STRING avaliada em runtime. Um typo -
//    'SCOPE_orders' sem o sufixo, ou 'SCOPE_orders:raed' - compila, sobe, e NEGA
//    todo mundo em silencio. Concentrar as strings aqui reduz a superficie de erro
//    de N controllers para um arquivo, e e o que a matriz de teste consegue cobrir.
// 2. Renomear um escopo vira uma edicao, nao uma varredura.
// 3. Quem le o controller ve a regra de negocio, nao a sintaxe do Spring Security.
//
// O prefixo SCOPE_ nao e escolha nossa: o JwtGrantedAuthoritiesConverter, padrao do
// resource server, le o claim "scope" do token e prefixa cada valor com "SCOPE_" ao
// transformar em GrantedAuthority. Por isso hasAuthority('SCOPE_x') e nao hasScope('x').
// Divisao de publicos das ESCRITAS (as leituras seguem so com escopo, porque o catalogo
// e lido por clients m2m - ecommerce-m2m, ordering - que nao carregam papel):
//
//   escrita de catalogo (products/categories/images/upload) -> escopo e NAO ser CUSTOMER.
//     Token de maquina passa (nao tem role); cliente da loja nunca passa, mesmo que um
//     token com o escopo vaze para ele.
//   escrita de ESTOQUE -> escopo e papel MANAGER. So humano MANAGER: client_credentials
//     nao carrega o claim role, entao maquina fica fora por construcao - e OPERATOR
//     tambem, coerente com a tabela auth_user_type_client_scope do authorization server.
public class SecurityAnnotations {

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_products:read')")
    public @interface CanReadProducts {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_products:write') and not hasRole('CUSTOMER')")
    public @interface CanWriteProducts {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_products:stock:write') and hasRole('MANAGER')")
    public @interface CanWriteProductsStock {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_categories:read')")
    public @interface CanReadCategories {}

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasAuthority('SCOPE_categories:write') and not hasRole('CUSTOMER')")
    public @interface CanWriteCategories {}
}
