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

import com.algaworks.algashop.product.catalog.presentation.ProductController;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

// @WebMvcTest: sobe APENAS a camada web do Spring (controllers, filters, etc.)
// SEM subir o contexto completo da aplicação (banco de dados, serviços, etc.).
// Isso torna o teste muito mais rápido que um @SpringBootTest completo.
@WebMvcTest(controllers = ProductController.class)
public class ProductBase {

    // O WebApplicationContext é o contexto Spring configurado pelo @WebMvcTest.
    // Ele contém o ProductController pronto para receber requisições.
    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        // RestAssuredMockMvc: versão do RestAssured que funciona com MockMvc.
        // O RestAssured é uma biblioteca popular para testar APIs REST em Java.
        // O MockMvc é a abstração do Spring para testar controllers sem HTTP real.
        // O RestAssuredMockMvc combina os dois: sintaxe fluente do RestAssured
        // com a leveza do MockMvc (sem precisar de um servidor HTTP real rodando).
        //
        // O código gerado pelo Spring Cloud Contract vai usar o RestAssuredMockMvc
        // para fazer as requisições definidas nos arquivos .groovy contra
        // o MockMvc configurado aqui.
        RestAssuredMockMvc.mockMvc(MockMvcBuilders.webAppContextSetup(context)
                // Garante que respostas com texto sejam sempre em UTF-8.
                // Importante para evitar problemas de encoding em campos com
                // caracteres especiais (acentos, símbolos, etc.).
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8).build());

        // Habilita o log completo de request e response quando um teste falha.
        // Sem isso, quando um contrato for violado, você veria apenas o erro.
        // Com isso, você vê exatamente o que foi enviado e o que foi recebido,
        // facilitando muito o diagnóstico do problema.
        RestAssuredMockMvc.enableLoggingOfRequestAndResponseIfValidationFails();
    }
}
