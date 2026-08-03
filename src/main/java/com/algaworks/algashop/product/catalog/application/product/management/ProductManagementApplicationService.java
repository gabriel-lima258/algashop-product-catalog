package com.algaworks.algashop.product.catalog.application.product.management;

import com.algaworks.algashop.product.catalog.application.ResourceNotFoundException;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.category.CategoryNotFoundException;
import com.algaworks.algashop.product.catalog.domain.category.CategoryRepository;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import com.algaworks.algashop.product.catalog.domain.product.ProductNotFoundException;
import com.algaworks.algashop.product.catalog.domain.product.ProductRepository;
import com.algaworks.algashop.product.catalog.domain.product.StockService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductManagementApplicationService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

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
    public void restock(UUID productId, int quantity) {
        Product product = findProduct(productId);
        stockService.restock(product, quantity);
    }

    public void withdraw(UUID productId, int quantity) {
        Product product = findProduct(productId);
        stockService.withdraw(product, quantity);
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
