// =============================================================================
// CLASSE BASE DE TESTES DE CONTRATO: ProductBase.java
// =============================================================================
//
// Esta classe existe porque o Spring Cloud Contract GERA testes automaticamente
// a partir dos arquivos .groovy, mas o código gerado precisa de um ponto de
// partida: uma classe base que configure o ambiente de teste.
//
// COMO O FRAMEWORK ENCONTRA ESTA CLASSE:
//   O build.gradle tem esta configuração:
//     contracts {
//         packageWithBaseClasses = "com.algaworks.algashop.product.catalog.contract.base"
//     }
//
//   O framework então cria o nome da classe base seguindo a convenção:
//     - Pega o nome do diretório do contrato (ex: "product/")
//     - Converte para PascalCase (ex: "Product")
//     - Adiciona o sufixo "Base" (ex: "ProductBase")
//     - Procura essa classe no pacote configurado acima
//
//   Portanto, contratos em src/contractTest/resources/contracts/PRODUCT/
//   precisam de uma classe chamada PRODUCTBase neste pacote.
//
//   Se você criar contratos para outro contexto (ex: contracts/category/),
//   precisará criar uma classe CategoryBase aqui também.
//
// O QUE O FRAMEWORK FAZ COM ESTA CLASSE:
//   O teste gerado automaticamente vai SE PARECER COM ISSO:
//
//     public class ProductTest extends ProductBase {  // ← estende esta classe
//         @Test
//         public void validate_findProductByIdV1() throws Exception {
//             // configura a requisição conforme o contrato .groovy
//             // executa a requisição contra o MockMvc configurado no setUp()
//             // valida o response conforme o contrato .groovy
//         }
//     }
//
//   O arquivo gerado fica em: build/generated-test-sources/
//   Você NÃO precisa (e não deve) editar esse arquivo — ele é regenerado
//   toda vez que você compila.
// =============================================================================

package com.algaworks.algashop.product.catalog.contract.base;

import com.algaworks.algashop.product.catalog.application.PageModel;
import com.algaworks.algashop.product.catalog.application.ResourceNotFoundException;
import com.algaworks.algashop.product.catalog.application.product.management.ProductInput;
import com.algaworks.algashop.product.catalog.application.product.management.ProductManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;
import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutputTestDataBuilder;
import com.algaworks.algashop.product.catalog.application.product.query.ProductQueryService;
import com.algaworks.algashop.product.catalog.presentation.ProductController;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.restdocs.operation.preprocess.Preprocessors;
import org.springframework.restdocs.templates.TemplateFormats;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;

// =============================================================================
// INTEGRAÇÃO SPRING CLOUD CONTRACT + SPRING REST DOCS
// =============================================================================
//
// Esta classe conecta dois frameworks com objetivos complementares:
//
//   SPRING CLOUD CONTRACT
//   └── Garante que o provider (este serviço) honra os contratos definidos
//       nos arquivos .groovy. Gera testes automaticamente a partir dos contratos.
//
//   SPRING REST DOCS
//   └── Gera documentação técnica (snippets .adoc) a partir de testes reais.
//       Documenta a API com exemplos capturados de requisições/respostas reais.
//
// A combinação permite: os testes de contrato que validam a API também
// geram automaticamente a documentação — documentação que, por definição,
// nunca fica desatualizada porque é gerada pelos próprios testes.
//
// FLUXO DE GERAÇÃO:
//   contrato .groovy
//     → Spring Cloud Contract gera ProductTest extends ProductBase
//       → teste executa via MockMvc configurado aqui (setUp)
//         → Spring REST Docs captura request/response reais
//           → gera snippets .adoc em build/generated-snippets/
//             → asciidoctor processa src/docs/asciidoc/index.adoc
//               → HTML final em build/docs/asciidoc/index.html
// =============================================================================

// @WebMvcTest: sobe APENAS a camada web do Spring (controllers, filters, etc.)
@WebMvcTest(controllers = ProductController.class)
@ExtendWith(RestDocumentationExtension.class)
public class ProductBase {

    // O WebApplicationContext é o contexto Spring configurado pelo @WebMvcTest.
    // Ele contém o ProductController pronto para receber requisições.
    @Autowired
    private WebApplicationContext context;

    // @MockitoBean substitui os beans reais no contexto Spring por mocks do Mockito.
    // Necessário porque @WebMvcTest não sobe a camada de serviço/repositório.
    @MockitoBean
    private ProductQueryService productQueryService;

    @MockitoBean
    private ProductManagementApplicationService productManagementApplicationService;

    // IDs fixos usados tanto nos contratos .groovy quanto nos mocks abaixo.
    public static final UUID validProductId = UUID.fromString("fffe6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID validUpdateProductId = UUID.fromString("ccce6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID invalidUpdateProductId = UUID.fromString("c77e6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID invalidProductId = UUID.fromString("216e6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID createNewProductId = UUID.fromString("aaae6ec2-7103-48b3-8e4f-3b58e43fb75a");
    public static final UUID deleteProductId = UUID.fromString("f1d3a7c4-6b2e-4f8a-9217-5d9c2e1b3a5f");
    public static final UUID deleteInvalidProductId = UUID.fromString("177e6ec2-7103-48b3-8e4f-3b58e43fb75a");

    // O RestDocumentationContextProvider é injetado pela RestDocumentationExtension.
    @BeforeEach
    void setUp(RestDocumentationContextProvider documentationContextProvider) {
        // RestAssuredMockMvc: versão do RestAssured que funciona com MockMvc.
        // O RestAssured é uma biblioteca popular para testar APIs REST em Java.
        // O MockMvc é a abstração do Spring para testar controllers sem HTTP real.
        //
        // O código gerado pelo Spring Cloud Contract vai usar o RestAssuredMockMvc
        // para fazer as requisições definidas nos arquivos .groovy contra
        // o MockMvc configurado aqui.
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.webAppContextSetup(context)
                        .apply(documentationConfiguration(documentationContextProvider)
                                // Define o formato de saída dos snippets como AsciiDoc (.adoc).
                                // Alternativa disponível: TemplateFormats.markdown() para .md.
                                .snippets().withTemplateFormat(TemplateFormats.asciidoctor())
                                .and().operationPreprocessors()
                                // Aplica pretty-print no corpo da resposta antes de salvar o snippet.
                                // Sem isso, JSON seria salvo em uma linha só, dificultando a leitura.
                                .withResponseDefaults(Preprocessors.prettyPrint()))
                        // Configura o REST Docs para documentar TODAS as requisições automaticamente.
                        // {ClassName} → nome da classe de teste gerada (ex: ProductTest)
                        // {methodName} → nome do método de teste (ex: validate_findProductByIdV1)
                        // Resultado: snippets salvos em build/generated-snippets/ProductTest/validate_findProductByIdV1/
                        // Este caminho é depois referenciado no index.adoc com:
                        //   include::{snippets}/ProductTest/validate_findProductByIdV1/http-request.adoc[]
                        .alwaysDo(MockMvcRestDocumentation.document("{ClassName}/{methodName}"))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8).build());

        // Habilita o log completo de request e response quando um teste falha.
        RestAssuredMockMvc.enableLoggingOfRequestAndResponseIfValidationFails();

        mockValidProductFindById();
        mockFilterProducts();
        mockCreateProduct();
        mockInvalidProductIdNotFound();
        mockUpdateProduct();
        mockInvalidProductIdUpdated();
        mockDeleteProductById();
        mockInvalidDeletedProductId();
    }

    private void mockCreateProduct() {
        // cria um novo produto com UUID novo
        Mockito.when(productManagementApplicationService.create(Mockito.any(ProductInput.class)))
                .thenReturn(createNewProductId);
        Mockito.when(productQueryService.findById(createNewProductId))
                .thenReturn(ProductDetailOutputTestDataBuilder.aProduct().build());
    }

    private void mockUpdateProduct() {
        Mockito.doNothing().when(productManagementApplicationService).update(Mockito.any(UUID.class), Mockito.any(ProductInput.class));
        Mockito.when(productQueryService.findById(validUpdateProductId))
                .thenReturn(ProductDetailOutputTestDataBuilder.aProductAlt().build());
    }

    private void mockInvalidProductIdUpdated() {
        Mockito.doThrow(new ResourceNotFoundException())
                .when(productManagementApplicationService)
                .update(Mockito.eq(invalidUpdateProductId), Mockito.any(ProductInput.class));
    }

    private void mockFilterProducts() {
        Mockito.when(productQueryService.filter(
                Mockito.anyInt(), Mockito.anyInt()
        )).then((answer) -> {
            Integer size = answer.getArgument(0);

            return PageModel.<ProductDetailOutput>builder()
                    .number(0)
                    .size(size)
                    .totalPages(1)
                    .totalElements(2)
                    .content(
                            List.of(
                                    ProductDetailOutputTestDataBuilder.aProduct().build(),
                                    ProductDetailOutputTestDataBuilder.aProductAlt().build()
                            )
                    ).build();
        });
    }

    private void mockValidProductFindById() {
        Mockito.when(productQueryService.findById(validProductId))
                .thenReturn(ProductDetailOutputTestDataBuilder.aProduct()
                        .id(validProductId)
                        .build());
    }

    private void mockInvalidProductIdNotFound() {
        Mockito.when(productQueryService.findById(invalidProductId))
                .thenThrow(new ResourceNotFoundException());
    }

    private void mockDeleteProductById() {
        Mockito.doNothing().when(productManagementApplicationService).disable(deleteProductId);
    }

    private void mockInvalidDeletedProductId() {
        Mockito.doThrow(new ResourceNotFoundException())
                .when(productManagementApplicationService)
                .disable(deleteInvalidProductId);
    }

}
