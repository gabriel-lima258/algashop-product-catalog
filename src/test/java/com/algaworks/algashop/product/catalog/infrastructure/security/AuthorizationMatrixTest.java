package com.algaworks.algashop.product.catalog.infrastructure.security;

import com.algaworks.algashop.product.catalog.application.category.management.CategoryManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryQueryService;
import com.algaworks.algashop.product.catalog.application.product.management.ProductImageManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.product.management.ProductManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductImageQueryService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductQueryService;
import com.algaworks.algashop.product.catalog.application.upload.UploadRequestApplicationService;
import com.algaworks.algashop.product.catalog.presentation.CategoryController;
import com.algaworks.algashop.product.catalog.presentation.ProductController;
import com.algaworks.algashop.product.catalog.presentation.ProductImagesController;
import com.algaworks.algashop.product.catalog.presentation.UploadRequestController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * A matriz de autorizacao do catalogo: para CADA rota anotada, tres perguntas.
 *
 *   sem token       -> 401 (nao autenticado)
 *   escopo errado   -> 403 (autenticado, sem permissao)
 *   escopo correto  -> passa pela seguranca
 *
 * Ver o javadoc do AuthorizationMatrixTest do ordering para as tres decisoes de desenho
 * que valem para os tres servicos: importar a SecurityConfig REAL, o JwtDecoder mockado
 * que existe so para o contexto subir, e a asercao positiva ser "nao e 401 nem 403".
 *
 * Especifico do catalogo: as rotas de estoque exigem products:stock:write, e NAO
 * products:write. Separar escrita de catalogo de escrita de saldo e deliberado - quem
 * integra estoque nao deveria ganhar de brinde o direito de reescrever preco.
 */
@WebMvcTest(controllers = {
        ProductController.class,
        CategoryController.class,
        ProductImagesController.class,
        UploadRequestController.class
})
@Import(ProductCatalogSecurityConfig.class)
class AuthorizationMatrixTest {

    private static final String UNRELATED_SCOPE = "SCOPE_totally:unrelated";
    private static final String JSON = "application/json";

    private static final String PRODUCT_ID = "2eea613a-3a11-46dd-95ee-2678c295559e";
    private static final String CATEGORY_ID = "1c2d3e4f-0000-0000-0000-000000000000";
    private static final String IMAGE_ID = "5a6b7c8d-0000-0000-0000-000000000000";

    private static final String PRODUCT_BODY = """
            {"name":"Produto","brand":"Marca","regularPrice":100.00,"salePrice":90.00,
             "enabled":true,"categoryId":"%s","description":"descricao"}""".formatted(CATEGORY_ID);

    private static final String CATEGORY_BODY = """
            {"name":"Categoria","enabled":true}""";

    private static final String IMAGE_BODY = """
            {"remoteFileName":"alguma-imagem.jpg"}""";

    private static final String UPLOAD_BODY = """
            {"originalFileName":"foto.jpg","contentLength":1024}""";

    private static final String STOCK_BODY = """
            {"quantity":1}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean private ProductQueryService productQueryService;
    @MockitoBean private ProductManagementApplicationService productManagementApplicationService;
    @MockitoBean private CategoryQueryService categoryQueryService;
    @MockitoBean private CategoryManagementApplicationService categoryManagementApplicationService;
    @MockitoBean private ProductImageManagementApplicationService productImageManagementApplicationService;
    @MockitoBean private ProductImageQueryService productImageQueryService;
    @MockitoBean private UploadRequestApplicationService uploadRequestApplicationService;

    static Stream<Arguments> routes() {
        return Stream.of(
                // PRODUTOS
                Arguments.of(HttpMethod.GET, "/api/v1/products", "SCOPE_products:read", null, null),
                Arguments.of(HttpMethod.GET, "/api/v1/products/" + PRODUCT_ID, "SCOPE_products:read", null, null),
                Arguments.of(HttpMethod.POST, "/api/v1/products", "SCOPE_products:write", JSON, PRODUCT_BODY),
                Arguments.of(HttpMethod.PUT, "/api/v1/products/" + PRODUCT_ID, "SCOPE_products:write", JSON, PRODUCT_BODY),
                Arguments.of(HttpMethod.PUT, "/api/v1/products/" + PRODUCT_ID + "/enable", "SCOPE_products:write", null, null),
                Arguments.of(HttpMethod.DELETE, "/api/v1/products/" + PRODUCT_ID + "/enable", "SCOPE_products:write", null, null),

                // ESTOQUE - escopo proprio, separado da escrita de catalogo
                Arguments.of(HttpMethod.POST, "/api/v1/products/" + PRODUCT_ID + "/restock", "SCOPE_products:stock:write", JSON, STOCK_BODY),
                Arguments.of(HttpMethod.POST, "/api/v1/products/" + PRODUCT_ID + "/withdraw", "SCOPE_products:stock:write", JSON, STOCK_BODY),

                // CATEGORIAS
                Arguments.of(HttpMethod.GET, "/api/v1/categories", "SCOPE_categories:read", null, null),
                Arguments.of(HttpMethod.GET, "/api/v1/categories/" + CATEGORY_ID, "SCOPE_categories:read", null, null),
                Arguments.of(HttpMethod.POST, "/api/v1/categories", "SCOPE_categories:write", JSON, CATEGORY_BODY),
                Arguments.of(HttpMethod.PUT, "/api/v1/categories/" + CATEGORY_ID, "SCOPE_categories:write", JSON, CATEGORY_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/v1/categories/" + CATEGORY_ID, "SCOPE_categories:write", null, null),

                // IMAGENS - herdam o escopo de produto, nao tem um proprio
                Arguments.of(HttpMethod.GET, "/api/v1/products/" + PRODUCT_ID + "/images", "SCOPE_products:read", null, null),
                Arguments.of(HttpMethod.GET, "/api/v1/products/" + PRODUCT_ID + "/images/" + IMAGE_ID, "SCOPE_products:read", null, null),
                Arguments.of(HttpMethod.POST, "/api/v1/products/" + PRODUCT_ID + "/images", "SCOPE_products:write", JSON, IMAGE_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/v1/products/" + PRODUCT_ID + "/images/" + IMAGE_ID, "SCOPE_products:write", null, null),
                Arguments.of(HttpMethod.PUT, "/api/v1/products/" + PRODUCT_ID + "/images/" + IMAGE_ID + "/primary", "SCOPE_products:write", null, null),

                // UPLOAD - a Fase 19 registrou este endpoint como "aberto, emite permissao
                // de escrita no bucket". A pendencia fecha aqui.
                Arguments.of(HttpMethod.POST, "/api/v1/upload-requests", "SCOPE_products:write", JSON, UPLOAD_BODY)
        );
    }

    @ParameterizedTest(name = "{0} {1} sem token -> 401")
    @MethodSource("routes")
    void shouldRejectRequestWithoutToken(HttpMethod method, String path, String scope,
                                         String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("rota sem token deveria ser 401").isEqualTo(401));
    }

    @ParameterizedTest(name = "{0} {1} com escopo errado -> 403")
    @MethodSource("routes")
    void shouldRejectRequestWithUnrelatedScope(HttpMethod method, String path, String scope,
                                               String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body)
                        .with(jwt().authorities(new SimpleGrantedAuthority(UNRELATED_SCOPE))))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("token autenticado sem o escopo %s deveria ser 403", scope).isEqualTo(403));
    }

    @ParameterizedTest(name = "{0} {1} com {2} -> passa pela seguranca")
    @MethodSource("routes")
    void shouldAllowRequestWithRequiredScope(HttpMethod method, String path, String scope,
                                             String contentType, String body) throws Exception {
        mockMvc.perform(request(method, path, contentType, body)
                        .with(jwt().authorities(new SimpleGrantedAuthority(scope))))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("com o escopo %s a requisicao nao deveria parar na seguranca", scope)
                        .isNotIn(401, 403));
    }

    /**
     * O escopo de escrita de catalogo NAO abre as rotas de estoque. Se alguem trocar
     * @CanWriteProductsStock por @CanWriteProducts em /restock ou /withdraw, este teste
     * e o unico lugar que percebe.
     */
    @ParameterizedTest(name = "products:write nao abre {0}")
    @ValueSource(strings = {"/restock", "/withdraw"})
    void shouldNotAllowStockOperationsWithPlainWriteScope(String suffix) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/products/" + PRODUCT_ID + suffix)
                        .contentType(JSON).content(STOCK_BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_products:write"))))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("escrita de catalogo nao deveria autorizar operacao de estoque")
                        .isEqualTo(403));
    }

    @ParameterizedTest(name = "{0} e publico")
    @ValueSource(strings = {"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"})
    void shouldNotRequireTokenOnHealthEndpoints(String path) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get(path))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("%s nao deveria exigir token", path).isNotIn(401, 403));
    }

    private static MockHttpServletRequestBuilder request(HttpMethod method, String path,
                                                        String contentType, String body) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
        if (contentType != null) {
            builder.contentType(contentType).content(body == null ? "{}" : body);
        }
        return builder;
    }
}
