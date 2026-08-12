package com.algaworks.algashop.product.catalog.infrastructure.web;

import com.algaworks.algashop.product.catalog.application.product.management.ProductManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductFilter;
import com.algaworks.algashop.product.catalog.application.product.query.ProductQueryService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductSummaryOutput;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import com.algaworks.algashop.product.catalog.presentation.ApiExceptionHandler;
import com.algaworks.algashop.product.catalog.presentation.ProductController;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cobre a cadeia inteira que o 400 de ?sortByProperty=salePrice&sortDirection=desc atravessa:
// conversores registrados no WebConfig -> binding do command object -> ApiExceptionHandler.
// Nenhuma dessas pecas sozinha provaria a correcao.
// addFilters = false: este teste e sobre BINDING de parametro de ordenacao, nao sobre
// seguranca. Com o starter de seguranca no classpath, o @WebMvcTest autoconfigura a
// cadeia PADRAO do Boot (nao a nossa ProductCatalogSecurityConfig) e toda requisicao
// vira 401 antes de o controller existir - as asercoes de 400 viravam 401.
// A cobertura de seguranca deste controller esta em ProductControllerSecurityTest.
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = ProductController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
@ImportAutoConfiguration(MessageSourceAutoConfiguration.class)
class SortParameterBindingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductQueryService productQueryService;

    @MockitoBean
    private ProductManagementApplicationService productManagementApplicationService;

    // o caso que o usuario reportou: os dois nomes do jeito que o OpenAPI publica
    @Test
    void shouldAcceptPropertyNameAndLowercaseDirection() throws Exception {
        mockFilter();

        mockMvc.perform(get("/api/v1/products")
                        .param("sortByProperty", "salePrice")
                        .param("sortDirection", "desc"))
                .andExpect(status().isOk());

        ProductFilter filter = capturedFilter();
        assertThat(filter.getSortByProperty()).isEqualTo(ProductFilter.SortType.SALE_PRICE);
        assertThat(filter.getSortDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // o que ja funcionava antes da correcao nao pode ter quebrado
    @Test
    void shouldKeepAcceptingEnumNameAndUppercaseDirection() throws Exception {
        mockFilter();

        mockMvc.perform(get("/api/v1/products")
                        .param("sortByProperty", "SALE_PRICE")
                        .param("sortDirection", "DESC"))
                .andExpect(status().isOk());

        assertThat(capturedFilter().getSortByProperty()).isEqualTo(ProductFilter.SortType.SALE_PRICE);
    }

    // valor invalido continua sendo 400 - a allowlist do enum e o ponto dela - mas a
    // mensagem lista o que vale, em vez de vazar nome de classe Java. e ela sai do proprio
    // enum: valor novo no SortType aparece aqui sozinho, sem editar nada
    @Test
    void shouldRejectUnknownPropertyWithReadableMessage() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sortByProperty", "hackme"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sortByProperty")
                        .value("Invalid value 'hackme'; must be one of: addedAt, salePrice"));
    }

    @Test
    void shouldRejectUnknownDirectionWithReadableMessage() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sortDirection", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.sortDirection")
                        .value("Invalid value 'sideways'; must be one of: asc, desc"));
    }

    private void mockFilter() {
        Mockito.when(productQueryService.filter(Mockito.any(ProductFilter.class)))
                .thenReturn(PageModel.<ProductSummaryOutput>builder()
                        .content(List.of())
                        .number(0)
                        .size(15)
                        .totalElements(0)
                        .totalPages(0)
                        .build());
    }

    private ProductFilter capturedFilter() {
        ArgumentCaptor<ProductFilter> captor = ArgumentCaptor.forClass(ProductFilter.class);
        Mockito.verify(productQueryService).filter(captor.capture());
        return captor.getValue();
    }
}
