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

import com.algaworks.algashop.product.catalog.application.category.management.CategoryInput;
import com.algaworks.algashop.product.catalog.application.category.management.CategoryManagementApplicationService;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryDetailOutput;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryFilter;
import com.algaworks.algashop.product.catalog.application.category.query.CategoryQueryService;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import com.algaworks.algashop.product.catalog.infrastructure.security.SecurityAnnotations.CanReadCategories;
import com.algaworks.algashop.product.catalog.infrastructure.security.SecurityAnnotations.CanWriteCategories;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;
    private final CategoryManagementApplicationService categoryManagementApplicationService;

    // cache http client side
    @GetMapping("/{categoryId}")
    @CanReadCategories
    public ResponseEntity<CategoryDetailOutput> findById(@PathVariable UUID categoryId) {
        CategoryDetailOutput category = categoryQueryService.findById(categoryId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .eTag("category:id:" + category.getId() + ":v:" + category.getVersion())
                .lastModified(category.getUpdatedAt().toInstant())
                .body(category);
    }

    @GetMapping
    @CanReadCategories
    public ResponseEntity<PageModel<CategoryDetailOutput>> filter(CategoryFilter filter, WebRequest webRequest) {

        // se não cacheado deixa consultar o banco sem cache
        if (!filter.isCacheable()) {
            PageModel<CategoryDetailOutput> result = categoryQueryService.filter(filter);
            return ResponseEntity.ok()
                    .body(result);
        }

        // usando a ultima categoria modificada como consulta
        OffsetDateTime lastModified = categoryQueryService.lastModified();

        // caso não foi modificado retorne status
        if (webRequest.checkNotModified(lastModified.toInstant().toEpochMilli())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        PageModel<CategoryDetailOutput> result = categoryQueryService.filter(filter);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .lastModified(lastModified.toInstant())
                .body(result);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CanWriteCategories
    public CategoryDetailOutput create(@RequestBody @Valid CategoryInput input) {
        UUID categoryId = categoryManagementApplicationService.create(input);
        return categoryQueryService.findById(categoryId);
    }

    @PutMapping("/{categoryId}")
    @CanWriteCategories
    public CategoryDetailOutput update(@PathVariable UUID categoryId, @RequestBody @Valid CategoryInput input) {
        categoryManagementApplicationService.update(categoryId, input);
        return categoryQueryService.findById(categoryId);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @CanWriteCategories
    public void update(@PathVariable UUID categoryId) {
        categoryManagementApplicationService.disable(categoryId);
    }

}
