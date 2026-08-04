package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.TestContainerMongoDBConfig;
import com.algaworks.algashop.product.catalog.infrastructure.persistence.MongoConfig;
import com.algaworks.algashop.product.catalog.infrastructure.persistence.dataload.DataLoadProperties;
import com.algaworks.algashop.product.catalog.infrastructure.persistence.dataload.DataLoader;
import com.algaworks.algashop.product.catalog.infrastructure.persistence.product.QuantityInStockAdjustmentMongoDBImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// O teste que justifica o modulo inteiro.
//
// Os testes sequenciais do QuantityInStockAdjustmentIT provam que a conta fecha; nenhum
// deles prova que ela continua fechando com duas threads em cima do mesmo documento - que
// e a unica pergunta que interessa aqui. Um findById + if + save passaria naquele e
// falharia neste.
//
// O CountDownLatch e o que torna o teste util: sem ele as threads sairiam escalonadas e a
// concorrencia poderia nunca acontecer. Todas ficam bloqueadas no mesmo portao e sao
// soltas juntas, maximizando a chance de colidirem de verdade.
@DataMongoTest
@Import({
        MongoConfig.class,
        QuantityInStockAdjustmentMongoDBImpl.class,
        DataLoader.class,
        DataLoadProperties.class,
        TestContainerMongoDBConfig.class
})
class QuantityInStockAdjustmentConcurrencyIT {

    @Autowired
    private QuantityInStockAdjustment quantityInStockAdjustment;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DataLoader dataLoader;

    // produto da massa de teste, com quantityInStock = 50
    private static final UUID EXISTING_PRODUCT = UUID.fromString("19274f99-e0d2-40b1-9b3a-912cb0982f11");
    private static final int INITIAL_STOCK = 50;

    @BeforeEach
    void beforeEach() throws Exception {
        dataLoader.run(new DefaultApplicationArguments());
    }

    // 20 saques de 5 sobre estoque de 50: cabem exatamente 10.
    // O ponto NAO e que 10 falhem - e que o estoque final seja 0 e nunca negativo.
    // Com leitura-decisao-escrita em Java, mais de 10 passariam e o saldo iria abaixo de zero
    @Test
    void shouldNotOversellUnderConcurrentWithdrawals() throws Exception {
        int threads = 20;
        int quantityEach = 5;

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();

        runConcurrently(threads, () -> {
            try {
                quantityInStockAdjustment.decrease(EXISTING_PRODUCT, quantityEach);
                succeeded.incrementAndGet();
            } catch (InsufficientStockException e) {
                insufficient.incrementAndGet();
            }
            return null;
        });

        int finalStock = currentStock();

        assertThat(succeeded.get()).isEqualTo(INITIAL_STOCK / quantityEach);
        assertThat(insufficient.get()).isEqualTo(threads - INITIAL_STOCK / quantityEach);
        assertThat(finalStock).isZero();
        // a asserção que resume tudo: em nenhuma ordem de execucao o estoque pode passar do zero
        assertThat(finalStock).isNotNegative();
    }

    // $inc concorrente nao perde escrita: o Mongo aplica cada incremento sobre o valor
    // corrente no servidor. Um `set(lido + 10)` em Java perderia atualizacoes aqui
    @Test
    void shouldNotLoseWritesUnderConcurrentRestocks() throws Exception {
        int threads = 20;
        int quantityEach = 3;

        runConcurrently(threads, () -> quantityInStockAdjustment.increase(EXISTING_PRODUCT, quantityEach));

        assertThat(currentStock()).isEqualTo(INITIAL_STOCK + threads * quantityEach);
    }

    // entradas e saidas misturadas: o saldo final tem que ser exatamente a soma algebrica
    @Test
    void shouldKeepStockConsistentUnderMixedOperations() throws Exception {
        int pairs = 15;

        List<Callable<Object>> work = IntStream.range(0, pairs * 2)
                .mapToObj(i -> (Callable<Object>) () -> i % 2 == 0
                        ? quantityInStockAdjustment.increase(EXISTING_PRODUCT, 4)
                        : quantityInStockAdjustment.decrease(EXISTING_PRODUCT, 2))
                .toList();

        runAll(work);

        assertThat(currentStock()).isEqualTo(INITIAL_STOCK + pairs * 4 - pairs * 2);
    }

    private int currentStock() {
        return productRepository.findById(EXISTING_PRODUCT).orElseThrow().getQuantityInStock();
    }

    private void runConcurrently(int threads, Callable<Object> task) throws Exception {
        runAll(IntStream.range(0, threads).mapToObj(i -> task).toList());
    }

    // solta todas as tarefas ao mesmo tempo e espera todas terminarem, propagando
    // qualquer excecao que nao tenha sido tratada dentro da propria tarefa
    private void runAll(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            List<Future<Object>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        startGate.await();
                        return task.call();
                    }))
                    .toList();

            startGate.countDown();

            for (Future<Object> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
