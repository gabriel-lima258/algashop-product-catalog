package com.algaworks.algashop.product.catalog.application.product.management;

import com.algaworks.algashop.product.catalog.application.ResourceNotFoundException;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.category.CategoryNotFoundException;
import com.algaworks.algashop.product.catalog.domain.category.CategoryRepository;
import com.algaworks.algashop.product.catalog.domain.product.*;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductManagementApplicationService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;

    private final StockService stockService;

    public UUID create(ProductInput input) {
        Product product = mapToProduct(input);
        productRepository.save(product);
        return product.getId();
    }

    public void update(UUID productId, ProductInput input) {
        Product product = findProduct(productId);
        Category category = findCategory(input.getCategoryId());

        updateProduct(product, input);
        product.setCategory(category);

        productRepository.save(product);
    }

    public void disable(UUID productId) {
        Product product = findProduct(productId);
        product.disable();
        productRepository.save(product);
    }

    public void enable(UUID productId) {
        Product product = findProduct(productId);
        product.enable();
        productRepository.save(product);
    }

    // O findProduct e o que garante o 404 antes de chegar ao estoque - e tambem a unica
    // leitura do agregado neste fluxo. Dali em diante ninguem carrega nem salva Product:
    // o ajuste acontece direto no banco, de forma atomica.
    //
    // Um detalhe que parece redundancia e nao e: o produto lido aqui pode ser apagado
    // entre esta linha e o ajuste. O adaptador trata esse caso e devolve 404 tambem -
    // duas checagens porque sao dois instantes diferentes
    //
    // O @Transactional esta aqui, e nao nos metodos acima, porque restock/withdraw sao os
    // unicos que escrevem em DUAS colecoes: o ajuste atomico em products e o registro em
    // stock_movements. Ou os dois entram, ou nenhum - sem isso o estoque poderia mudar e o
    // movimento se perder, deixando um saldo certo que nenhum historico explica.
    // Create/update/disable/enable fazem um save so, e no Mongo a escrita de UM documento
    // ja e atomica por natureza: transacao ali nao acrescentaria nada.
    //
    // Dois efeitos que vem de brinde e vale ter em mente:
    // - os eventos do StockService rodam em @EventListener comum, ou seja, sincronos e
    //   dentro desta transacao. Se um listener estourar, o rollback desfaz o ajuste de
    //   estoque junto - o evento nao e um "depois", e parte do mesmo commit
    // - o findAndModify do adaptador entra na transacao porque o MongoOperations usa a
    //   sessao que o MongoTransactionManager amarrou a thread (ver MongoConfig)
    @Transactional
    public void restock(UUID productId, int quantity) {
        Product product = findProduct(productId);
        StockMovement movement = stockService.restock(product, quantity);
        stockMovementRepository.save(movement);
    }

    @Transactional
    public void withdraw(UUID productId, int quantity) {
        Product product = findProduct(productId);
        StockMovement movement = stockService.withdraw(product, quantity);
        stockMovementRepository.save(movement);
    }

    private Product mapToProduct(ProductInput input) {
        Category category = findCategory(input.getCategoryId());

        return Product.builder()
                .name(input.getName())
                .brand(input.getBrand())
                .description(input.getDescription())
                .regularPrice(input.getRegularPrice())
                .salePrice(input.getSalePrice())
                .enabled(input.getEnabled())
                .category(category)
                .build();
    }

    private void updateProduct(Product product, ProductInput input) {
        product.setName(input.getName());
        product.setBrand(input.getBrand());
        product.setDescription(input.getDescription());
        product.changePrice(input.getRegularPrice(), input.getSalePrice());
        product.setEnabled(input.getEnabled());
    }

    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Category findCategory(@NotNull UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}
