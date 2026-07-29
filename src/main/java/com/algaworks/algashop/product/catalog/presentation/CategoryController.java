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
import com.algaworks.algashop.product.catalog.application.category.query.CategoryQueryService;
import com.algaworks.algashop.product.catalog.application.util.PageModel;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQueryService;

    private final CategoryManagementApplicationService categoryManagementApplicationService;

    @GetMapping("/{categoryId}")
    public CategoryDetailOutput findById(@PathVariable UUID categoryId) {
        return categoryQueryService.findById(categoryId);
    }

    @GetMapping
    public PageModel<CategoryDetailOutput> filter(
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "number", required = false) Integer number
    ) {
        return categoryQueryService.filter(size, number);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDetailOutput create(@RequestBody @Valid CategoryInput input) {
        UUID categoryId = categoryManagementApplicationService.create(input);
        return categoryQueryService.findById(categoryId);
    }

    @PutMapping("/{categoryId}")
    public CategoryDetailOutput update(@PathVariable UUID categoryId, @RequestBody @Valid CategoryInput input) {
        categoryManagementApplicationService.update(categoryId, input);
        return categoryQueryService.findById(categoryId);
    }

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID categoryId) {
        categoryManagementApplicationService.disable(categoryId);
    }

}
