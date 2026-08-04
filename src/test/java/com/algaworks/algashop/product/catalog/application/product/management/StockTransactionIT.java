package com.algaworks.algashop.product.catalog.application.product.management;

import com.algaworks.algashop.product.catalog.TestContainerMongoDBConfig;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import com.algaworks.algashop.product.catalog.domain.product.ProductRepository;
import com.algaworks.algashop.product.catalog.domain.product.StockMovement;
import com.algaworks.algashop.product.catalog.domain.product.StockMovementRepository;
import com.algaworks.algashop.product.catalog.infrastructure.persistence.dataload.DataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

// O teste que justifica o replica set.
//
// O restock/withdraw escreve em DUAS colecoes: o findAndModify atomico em products e o
// registro em stock_movements. Cada uma delas, sozinha, ja era atomica - o Mongo garante
// isso para um documento. O que NAO era garantido e que as duas caissem juntas, e e essa
// a unica pergunta aqui.
//
// Sem a garantia, a falha nao e barulhenta: o estoque baixa, o movimento se perde, e sobra
// um saldo correto que nenhum historico explica. Ninguem percebe ate precisar auditar.
//
// Por que @SpringBootTest e nao uma fatia como @DataMongoTest: aqui e preciso o application
// service real, o MongoTransactionManager e o proxy transacional do Spring ao mesmo tempo.
// Uma fatia de persistencia nao carrega o proxy - o @Transactional viraria enfeite e o
// teste passaria por motivo errado, que e a pior forma de passar.
//
// O Mongo vem do TestContainerMongoDBConfig, que sobe um replica set descartavel: sem
// replica set nao existe transacao, e este teste sequer subiria.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestContainerMongoDBConfig.class)
class StockTransactionIT {

    private static final UUID EXISTING_PRODUCT = UUID.fromString("19274f99-e0d2-40b1-9b3a-912cb0982f11");
    private static final int INITIAL_STOCK = 50;

    @Autowired
    private ProductManagementApplicationService productManagementApplicationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MongoOperations mongoOperations;

    @Autowired
    private DataLoader dataLoader;

    // Spy, e nao mock: por padrao ele delega para o repositorio de verdade, entao o caminho
    // feliz grava de verdade. So o teste de rollback troca o comportamento, e so do save
    @MockitoSpyBean
    private StockMovementRepository stockMovementRepository;

    @BeforeEach
    void beforeEach() throws Exception {
        dataLoader.run(new DefaultApplicationArguments());
        // o DataLoader nao conhece stock_movements - ela nao esta nas sources do YAML.
        // Sem esta linha os movimentos de um teste vazariam para o seguinte
        mongoOperations.remove(new Query(), StockMovement.class);
    }

    @Test
    void shouldWriteStockAndMovementTogether() {
        productManagementApplicationService.withdraw(EXISTING_PRODUCT, 10);

        assertThat(stockOf(EXISTING_PRODUCT)).isEqualTo(INITIAL_STOCK - 10);

        List<StockMovement> movements = mongoOperations.findAll(StockMovement.class);
        assertThat(movements).hasSize(1);

        StockMovement movement = movements.getFirst();
        assertThat(movement.getProductId()).isEqualTo(EXISTING_PRODUCT);
        assertThat(movement.getType()).isEqualTo(StockMovement.MovementType.STOCK_OUT);
        assertThat(movement.getMovementQuantity()).isEqualTo(10);
        // o extrato tem que bater com o saldo, e nao apenas existir
        assertThat(movement.getPreviousQuantity()).isEqualTo(INITIAL_STOCK);
        assertThat(movement.getNewQuantity()).isEqualTo(stockOf(EXISTING_PRODUCT));
    }

    // O cenario que o @Transactional existe para cobrir: o ajuste de estoque JA aconteceu
    // no banco quando o registro do movimento falha. Sem transacao, o findAndModify fica
    // commitado e nada o desfaz
    @Test
    void shouldRollbackTheStockAdjustmentWhenTheMovementFails() {
        doThrow(new DataAccessResourceFailureException("stock_movements indisponivel"))
                .when(stockMovementRepository).save(any(StockMovement.class));

        assertThatExceptionOfType(DataAccessResourceFailureException.class)
                .isThrownBy(() -> productManagementApplicationService.withdraw(EXISTING_PRODUCT, 10));

        // as duas afirmacoes sao o teste: nem meia operacao ficou
        assertThat(stockOf(EXISTING_PRODUCT)).isEqualTo(INITIAL_STOCK);
        assertThat(mongoOperations.count(new Query(), StockMovement.class)).isZero();
    }

    // vale nos dois sentidos - entrada de estoque tambem escreve nas duas colecoes
    @Test
    void shouldRollbackTheRestockWhenTheMovementFails() {
        doThrow(new DataAccessResourceFailureException("stock_movements indisponivel"))
                .when(stockMovementRepository).save(any(StockMovement.class));

        assertThatExceptionOfType(DataAccessResourceFailureException.class)
                .isThrownBy(() -> productManagementApplicationService.restock(EXISTING_PRODUCT, 25));

        assertThat(stockOf(EXISTING_PRODUCT)).isEqualTo(INITIAL_STOCK);
        assertThat(mongoOperations.count(new Query(), StockMovement.class)).isZero();
    }

    // saldo insuficiente e outra coisa: nada chega a ser escrito, entao nao ha o que
    // desfazer. O que se afirma aqui e que a recusa nao deixa movimento orfao
    @Test
    void shouldNotRecordAMovementWhenTheWithdrawIsRefused() {
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> productManagementApplicationService.withdraw(EXISTING_PRODUCT, INITIAL_STOCK + 1));

        assertThat(stockOf(EXISTING_PRODUCT)).isEqualTo(INITIAL_STOCK);
        assertThat(mongoOperations.count(new Query(), StockMovement.class)).isZero();
    }

    // le fora da transacao, de proposito: o que interessa e o que sobrou commitado
    private int stockOf(UUID productId) {
        Product product = productRepository.findById(productId).orElseThrow();
        return product.getQuantityInStock();
    }
}
