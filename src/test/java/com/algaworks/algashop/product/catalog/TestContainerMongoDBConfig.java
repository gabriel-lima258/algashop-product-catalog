package com.algaworks.algashop.product.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

// O Mongo que a suite usa. Nao e o do docker-compose: cada execucao sobe o seu proprio,
// descartavel, e por isso nenhum teste depende de infraestrutura de pe nem pode sujar o
// banco de desenvolvimento.
//
// Por que um replica set, se o teste e de um documento so: TRANSACAO no Mongo so existe
// dentro de um replica set. Sem isso o StockTransactionIT nem subiria - o @Transactional
// falharia com "Transaction numbers are only allowed on a replica set member or mongos".
// Ver transacoes-mongo.md.
//
// withReplicaSet() e o que liga esse modo. No Testcontainers 1.x o MongoDBContainer subia
// como replica set por padrao; no 2.x isso virou opt-in, e sem a chamada o container roda
// como no unico - o teste de transacao morre com "Transaction numbers are only allowed on
// a replica set member or mongos".
//
// Este metodo faz o container passar o --replSet e rodar o rs.initiate() sozinho, deixando
// o proprio Mongo escolher o endereco que anuncia como membro. Nao se anuncia endereco a
// mao aqui de proposito: o membro anunciado e o endereco que o driver persegue depois de
// descobrir a topologia, e escrever "localhost:27017" ali aponta para o mesmo host:porta do
// cluster de desenvolvimento - que por acaso tambem se chama rs0. Funciona; o que se perde
// e a garantia de que a suite nao PODE chegar no banco de desenvolvimento.
//
// static, e nao @Container: um container por JVM, compartilhado por todas as classes de
// teste, em vez de um por classe. Subir o Mongo custa segundos; multiplicar isso por
// classe e o que transforma uma suite em algo que ninguem roda. Nao ha stop() explicito -
// quem derruba e o Ryuk, o container-vigia do Testcontainers, quando a JVM morre.
@TestConfiguration
public class TestContainerMongoDBConfig {

    private static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer("mongo:8").withReplicaSet();

    static {
        mongoDBContainer.start();
    }

    // @ServiceConnection dispensa qualquer @DynamicPropertySource: o Boot le a porta
    // mapeada do container - que e sorteada a cada execucao - e sobrescreve a URI do
    // application-test-env.yml. E por isso que a URI daquele arquivo nao e o que a suite
    // usa quando esta configuracao esta importada
    @Bean
    @ServiceConnection
    public MongoDBContainer mongoDBContainer() {
        return mongoDBContainer;
    }
}
