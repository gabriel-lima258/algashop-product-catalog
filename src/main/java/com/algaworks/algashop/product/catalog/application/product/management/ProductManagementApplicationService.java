package com.algaworks.algashop.product.catalog.application.product.management;

import com.algaworks.algashop.product.catalog.application.ResourceNotFoundException;
import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;
import com.algaworks.algashop.product.catalog.application.util.CacheNames;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.domain.category.Category;
import com.algaworks.algashop.product.catalog.domain.category.CategoryNotFoundException;
import com.algaworks.algashop.product.catalog.domain.category.CategoryRepository;
import com.algaworks.algashop.product.catalog.domain.product.*;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

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

    private final Mapper mapper;

    // WRITE-THROUGH: grava no banco e no cache na MESMA operacao, sem esperar alguem
    // pedir. O contrario do cache-aside do findById, que so popula depois do primeiro
    // miss - aqui o produto ja nasce quente.
    //
    // Repare no que isso custou: create deixou de devolver UUID e passou a devolver
    // ProductDetailOutput. Nao foi capricho - o key = "#result.id" precisa de um
    // retorno com id, e o valor guardado no cache e justamente o que o metodo devolve.
    // Uma decisao de cache mudou a assinatura de um metodo de aplicacao, e vale saber
    // que essa e a natureza do @CachePut: ele cacheia o RETORNO, entao o retorno tem
    // que ser o que se quer cachear.
    //
    // O condition existe porque produto desabilitado nao deve ocupar cache: ele nao
    // aparece na listagem e quase nunca e consultado.
    @CachePut(cacheNames = CacheNames.PRODUCTS, key = "#result.id",
              condition = "#input.enabled == true")
    public ProductDetailOutput create(ProductInput input) {
        Product product = mapToProduct(input);
        productRepository.save(product);

        return mapper.convert(product, ProductDetailOutput.class);
    }

    // As duas anotacoes convivem porque as condicoes sao EXCLUDENTES: habilitado
    // reescreve a entrada, desabilitado a remove. Sem o par, desabilitar um produto por
    // update deixaria a versao antiga - ainda habilitada - servindo do cache ate o TTL.
    @CachePut(cacheNames = CacheNames.PRODUCTS, key = "#result.id",
              condition = "#input.enabled == true")
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId",
              condition = "#input.enabled == false")
    public ProductDetailOutput update(UUID productId, ProductInput input) {
        Product product = findProduct(productId);
        Category category = findCategory(input.getCategoryId());

        updateProduct(product, input);
        product.setCategory(category);

        productRepository.save(product);

        return mapper.convert(product, ProductDetailOutput.class);
    }

    // toda vez que um produto e desativado invalidamos seu cache
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public void disable(UUID productId) {
        Product product = findProduct(productId);
        product.disable();
        productRepository.save(product);
    }

    // O par do disable. Faltava, e a assimetria era silenciosa: reabilitar um produto
    // nao invalidava nada, entao quem tivesse lido a versao desabilitada continuava
    // recebendo enabled: false ate o TTL. Todo metodo que muda estado cacheado precisa
    // dizer o que fazer com o cache - inclusive para dizer "nada"
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
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
    //
    // -------------------------------------------------------------------------
    //
    // O @CacheEvict aqui faltava, e a falta era facil de nao ver: nem restock nem
    // withdraw carregam ou salvam o Product - o ajuste vai direto ao banco por
    // findAndModify. Nada nesses metodos "parece" mexer no produto. Mas quantityInStock
    // muda, e com ele o inStock que o ProductDetailOutput carrega - entao a entrada em
    // cache passa a mentir sobre disponibilidade, que e justamente o campo que alguem
    // consulta antes de comprar.
    //
    // ORDEM em relacao a transacao - o detalhe que vale entender aqui:
    //
    // O que E garantido: beforeInvocation = false (o padrao) faz a evicao rodar DEPOIS
    // do metodo, e SO se ele retornar sem excecao. Saque recusado por saldo insuficiente
    // estoura InsufficientStockException, entao nao evicta nada - correto, ja que o
    // banco tambem nao mudou.
    //
    // O que NAO e garantido: a ordem entre o interceptador de cache e o de transacao.
    // Os dois registram seus advisors com LOWEST_PRECEDENCE por padrao, e o desempate
    // fica por conta da ordem de registro. Na pratica isso significa que a evicao pode
    // cair antes ou depois do commit, e nao da para depender de uma das duas.
    //
    // Qual dos dois lados dessa moeda machuca: evicao depois do commit e o caso benigno.
    // Antes do commit abre uma janela em que outra thread lê o banco ainda no valor
    // antigo e REPOPULA o cache com ele - dado velho de volta, agora com o TTL inteiro
    // pela frente. Fechar isso de vez pediria @TransactionalEventListener(AFTER_COMMIT)
    // publicando a evicao, ou @EnableCaching(order = ...) forcando a precedencia. Fica
    // registrado como pendencia em docs/01-arquitetura-design/cache.md; o TTL curto e o
    // que limita o estrago enquanto isso.
    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public void restock(UUID productId, int quantity) {
        Product product = findProduct(productId);
        StockMovement movement = stockService.restock(product, quantity);
        stockMovementRepository.save(movement);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
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
