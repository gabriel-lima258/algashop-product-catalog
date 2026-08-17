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
import com.algaworks.algashop.product.catalog.application.product.query.ProductFilter;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;
import com.algaworks.algashop.product.catalog.application.product.query.ProductQueryService;
import com.algaworks.algashop.product.catalog.application.product.query.ProductSummaryOutput;
import com.algaworks.algashop.product.catalog.domain.category.CategoryNotFoundException;
import com.algaworks.algashop.product.catalog.infrastructure.security.check.SecurityAnnotations.CanReadProducts;
import com.algaworks.algashop.product.catalog.infrastructure.security.check.SecurityAnnotations.CanWriteProducts;
import com.algaworks.algashop.product.catalog.infrastructure.security.check.SecurityAnnotations.CanWriteProductsStock;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductQueryService productQueryService;

    private final ProductManagementApplicationService productManagementApplicationService;

    @GetMapping("/{productId}")
    @CanReadProducts
    public ResponseEntity<ProductDetailOutput> findById(@PathVariable UUID productId) {
        ProductDetailOutput product = productQueryService.findById(productId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic())
                .eTag("product:id" + product.getId() + ":v:" + product.getVersion())
                .lastModified(product.getUpdatedAt().toInstant())
                .body(product);
    }

    @GetMapping
    @CanReadProducts
    public PageModel<ProductSummaryOutput> filter(ProductFilter filter) {
        return productQueryService.filter(filter);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CanWriteProducts
    public ProductDetailOutput create(@RequestBody @Valid ProductInput input) {
        try {
            return productManagementApplicationService.create(input);
        } catch (CategoryNotFoundException e) {
            throw new UnprocessableContentException(e.getMessage(), e);
        }
    }

    @PutMapping("/{productId}")
    @CanWriteProducts
    public ProductDetailOutput update(@PathVariable UUID productId, @RequestBody @Valid ProductInput input) {
        return productManagementApplicationService.update(productId, input);
    }

    @DeleteMapping("/{productId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CanWriteProducts
    public void disable(@PathVariable UUID productId) {
        productManagementApplicationService.disable(productId);
    }

    @PutMapping("/{productId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CanWriteProducts
    public void enable(@PathVariable UUID productId) {
        productManagementApplicationService.enable(productId);
    }

    // POST e nao PUT/PATCH: reposicao e saque sao OPERACOES, nao atribuicao de valor.
    // Um PATCH { "quantityInStock": 40 } seria "o estoque agora e 40", e duas dessas
    // chegando juntas se sobrescreveriam - o classico lost update. "Some 10" e "subtraia
    // 10" compoem; "passe a valer 40" nao.
    //
    // Respostas: 204 no sucesso, 404 se o produto nao existe, 422 se falta saldo
    // (InsufficientStockException). Ver docs/02-persistencia/concorrencia-e-atomicidade.md
    @PostMapping("/{productId}/restock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CanWriteProductsStock
    public void restock(@PathVariable UUID productId, @RequestBody @Valid ProductQuantityModel productQuantityModel) {
        productManagementApplicationService.restock(productId, productQuantityModel.getQuantity());
    }

    @PostMapping("/{productId}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CanWriteProductsStock
    public void withdraw(@PathVariable UUID productId, @RequestBody @Valid ProductQuantityModel productQuantityModel) {
        productManagementApplicationService.withdraw(productId, productQuantityModel.getQuantity());
    }
}
