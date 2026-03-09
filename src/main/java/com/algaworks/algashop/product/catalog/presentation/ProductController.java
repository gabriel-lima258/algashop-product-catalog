// =============================================================================
// PROVIDER (FORNECEDOR) DO CONTRATO: ProductController.java
// =============================================================================
//
// No Contract-Driven Development, este controller é o PROVIDER —
// o serviço que "promete" fornecer dados em um formato específico.
//
// A responsabilidade do provider é HONRAR OS CONTRATOS definidos nos
// arquivos .groovy. Os testes de contrato gerados automaticamente
// executam requisições REAIS contra este controller para verificar
// que ele ainda está cumprindo todos os acordos.
//
// Se você alterar este controller de forma que quebre algum contrato
// (remover um campo, mudar o tipo, etc.), o teste de contrato gerado
// vai FALHAR, alertando você antes de ir para produção.
//
// FLUXO DE VERIFICAÇÃO:
//   1. ./gradlew contractTest
//   2. Spring Cloud Contract lê os .groovy em src/contractTest/resources/contracts/
//   3. Gera testes JUnit em build/generated-test-sources/
//   4. Esses testes estendem ProductBase e executam contra este controller
//   5. Se o controller retornar algo diferente do contrato → FALHA
// =============================================================================

package com.algaworks.algashop.product.catalog.presentation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    // STUB: implementação temporária para desenvolvimento com CDC. Como se fosse um servidor fake
    //
    // O que é um stub neste contexto?
    // Um stub é uma implementação simplificada que retorna dados fixos (hardcoded)
    // sem conectar a banco de dados, serviços externos, etc.
    //
    // Por que usar stub aqui?
    // No CDC, você pode definir e validar os contratos ANTES de implementar
    // a lógica de negócio real. Isso permite:
    //   - Desenvolver o consumer (outro serviço) em paralelo, usando o stub/contrato
    //   - Ter testes de contrato passando desde o início
    //   - Evoluir a implementação real sem quebrar os acordos existentes
    //
    // Quando a implementação real estiver pronta, este stub será substituído
    // pela lógica real (chamando serviços de aplicação, repositórios, etc.),
    // mas o CONTRATO (.groovy) permanecerá o mesmo — garantindo compatibilidade.
    @GetMapping("/{productId}")
    public ProductDetailOutput findById(@PathVariable UUID productId) {
        return ProductDetailOutput.builder()
                .id(productId)
                .addedAt(OffsetDateTime.now())
                .name("Notebook X11")
                .brand("Deep Diver")
                .description("A Gamer Notebook")
                .regularPrice(new BigDecimal("1500.00"))
                .salePrice(new BigDecimal("1000.00"))
                .inStock(true)
                .enabled(true)
                .category(CategoryMinimalOutput.builder()
                        .id(UUID.randomUUID())
                        .name("Notebook")
                        .build())
                .build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailOutput create(@RequestBody @Valid ProductInput input) {
        return ProductDetailOutput.builder()
                .id(UUID.randomUUID())
                .addedAt(OffsetDateTime.now())
                .inStock(false)
                .name(input.getName())
                .brand(input.getBrand())
                .description(input.getDescription())
                .regularPrice(input.getRegularPrice())
                .salePrice(input.getSalePrice())
                .enabled(input.getEnabled())
                .category(
                        CategoryMinimalOutput.builder()
                                .id(input.getCategoryId())
                                .name("Notebook")
                                .build()
                )
                .build();

    }
}
