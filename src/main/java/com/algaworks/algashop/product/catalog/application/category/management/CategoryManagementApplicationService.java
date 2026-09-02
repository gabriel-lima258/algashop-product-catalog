package com.algaworks.algashop.product.catalog.application.category.management;

import com.algaworks.algashop.product.catalog.application.LocalEventPublisher;
import com.algaworks.algashop.product.catalog.application.category.event.CategoryUpdatedEvent;
import com.algaworks.algashop.product.catalog.application.util.CacheNames;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.category.CategoryNotFoundException;
import com.algaworks.algashop.product.catalog.domain.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryManagementApplicationService {

    private final CategoryRepository categoryRepository;
    private final LocalEventPublisher localEventPublisher;


    // Criar categoria nao mexe em nenhuma categoria existente, entao nao ha entrada
    // individual a invalidar - mas a LISTAGEM mudou, e ela esta cacheada sob a chave fixa
    // 'default'. Sem este evict, a categoria nova so apareceria quando o TTL expirasse.
    //
    // Note que create NAO publica CategoryUpdatedEvent, e isso continua correto: categoria
    // recem-criada nao tem produto nenhum apontando para ela, entao nao existe copia
    // desnormalizada a sincronizar.
    @CacheEvict(cacheNames = CacheNames.CATEGORIES_FILTER, key = "'default'")
    public UUID create(CategoryInput input) {
        Category category = new Category(input.getName(), input.getEnabled());
        categoryRepository.save(category);
        return category.getId();
    }

    // Aqui sao DOIS caches, porque a mesma escrita torna duas coisas obsoletas: a
    // categoria em si e a listagem em que ela aparece. @Caching e so o involucro que
    // permite empilhar mais de um @CacheEvict no mesmo metodo.
    @Caching(
            evict = {
                    @CacheEvict(cacheNames = CacheNames.CATEGORIES_FILTER, key = "'default'"),
                    @CacheEvict(cacheNames = CacheNames.CATEGORIES, key = "#categoryId")
            }
    )
    public void update(UUID categoryId, CategoryInput input) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        category.setName(input.getName());
        category.setEnabled(input.getEnabled());
        categoryRepository.save(category);

        // publica DEPOIS de gravar, nunca antes: o evento afirma um fato consumado, e
        // consumidor que reagisse a uma gravacao que ainda pode falhar propagaria mentira.
        // dai em diante o listener assume - ver infrastructure/listener/category
        localEventPublisher.send(new CategoryUpdatedEvent(
                category.getId(),
                category.getName(),
                category.getEnabled()
        ));
    }

    // Faltava, e era o pior dos tres esquecimentos: o filtro default pede
    // enabled = true, entao desabilitar uma categoria a TIRA da listagem - e sem evict
    // ela continuava aparecendo, com enabled: true, ate o TTL. O cache servia um estado
    // que o banco ja tinha contradito.
    //
    // Mesmos dois caches do update, pela mesma razao: muda a categoria e muda a listagem.
    @Caching(
            evict = {
                    @CacheEvict(cacheNames = CacheNames.CATEGORIES_FILTER, key = "'default'"),
                    @CacheEvict(cacheNames = CacheNames.CATEGORIES, key = "#categoryId")
            }
    )
    public void disable(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        category.setEnabled(false);
        categoryRepository.save(category);

        // mesmo evento do update: desabilitar tambem e uma mudanca que a copia dentro dos
        // produtos precisa refletir (o campo category.enabled)
        localEventPublisher.send(new CategoryUpdatedEvent(
                category.getId(),
                category.getName(),
                category.getEnabled()
        ));
    }
}
