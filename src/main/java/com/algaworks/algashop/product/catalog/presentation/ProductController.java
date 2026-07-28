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

import com.algaworks.algashop.product.catalog.application.product.management.ProductInput;
import com.algaworks.algashop.product.catalog.application.product.management.ProductManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.PageModel;
import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;
import com.algaworks.algashop.product.catalog.application.product.query.ProductQueryService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductSummaryOutput;
import com.algaworks.algashop.product.catalog.domain.category.CategoryNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductQueryService productQueryService;

    private final ProductManagementApplicationService productManagementApplicationService;

    @GetMapping("/{productId}")
    public ProductDetailOutput findById(@PathVariable UUID productId) {
        return productQueryService.findById(productId);
    }

    @GetMapping
    public PageModel<ProductSummaryOutput> filter(
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "number", required = false) Integer number
    ) {
        return productQueryService.filter(size, number);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailOutput create(@RequestBody @Valid ProductInput input) {
        UUID productId;

        try {
            productId = productManagementApplicationService.create(input);
        } catch (CategoryNotFoundException e) {
            throw new UnprocessableContentException(e.getMessage(), e);
        }

        return productQueryService.findById(productId);
    }

    @PutMapping("/{productId}")
    public ProductDetailOutput update(@PathVariable UUID productId, @RequestBody @Valid ProductInput input) {
        productManagementApplicationService.update(productId, input);
        return productQueryService.findById(productId);
    }

    @DeleteMapping("/{productId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID productId) {
        productManagementApplicationService.disable(productId);
    }

    @PutMapping("/{productId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable UUID productId) {
        productManagementApplicationService.enable(productId);
    }

}
