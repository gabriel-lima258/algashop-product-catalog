package com.algaworks.algashop.product.catalog.application.category.management;

import com.algaworks.algashop.product.catalog.application.ApplicationMessagePublisher;
import com.algaworks.algashop.product.catalog.application.ResourceNotFoundException;
import com.algaworks.algashop.product.catalog.application.category.event.CategoryUpdatedEvent;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.category.CategoryNotFoundException;
import com.algaworks.algashop.product.catalog.domain.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryManagementApplicationService {

    private final CategoryRepository categoryRepository;
    private final ApplicationMessagePublisher applicationMessagePublisher;

    // create NAO publica evento, e isso e correto: categoria recem-criada nao tem produto
    // nenhum apontando para ela, entao nao existe copia desnormalizada a sincronizar
    public UUID create(CategoryInput input) {
        Category category = new Category(input.getName(), input.getEnabled());
        categoryRepository.save(category);
        return category.getId();
    }

    public void update(UUID categoryId, CategoryInput input) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        category.setName(input.getName());
        category.setEnabled(input.getEnabled());
        categoryRepository.save(category);

        // publica DEPOIS de gravar, nunca antes: o evento afirma um fato consumado, e
        // consumidor que reagisse a uma gravacao que ainda pode falhar propagaria mentira.
        // dai em diante o listener assume - ver infrastructure/listener/category
        applicationMessagePublisher.send(new CategoryUpdatedEvent(
                category.getId(),
                category.getName(),
                category.getEnabled()
        ));
    }

    public void disable(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        category.setEnabled(false);
        categoryRepository.save(category);

        // mesmo evento do update: desabilitar tambem e uma mudanca que a copia dentro dos
        // produtos precisa refletir (o campo category.enabled)
        applicationMessagePublisher.send(new CategoryUpdatedEvent(
                category.getId(),
                category.getName(),
                category.getEnabled()
        ));
    }
}
