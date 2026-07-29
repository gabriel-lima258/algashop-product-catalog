package com.algaworks.algashop.product.catalog.infrastructure.persistence.dataload;

import com.algaworks.algashop.product.catalog.infrastructure.util.AlgaShopResourceUtils;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.BsonArray;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// Carga de massa de teste no Mongo. O equivalente ao Flyway do ordering/billing -
// so que Mongo nao tem migration, entao a carga e feita na mao a cada subida.
// ApplicationRunner: roda UMA vez, depois do contexto estar pronto (diferente de
// @PostConstruct, que roda no meio da inicializacao, quando o Mongo pode nao estar acessivel).
@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements ApplicationRunner {

    private final MongoOperations mongoOperations;
    private final DataLoadProperties properties;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 1. interruptor geral - em producao isso tem que estar desligado
        if (!properties.getEnabled()) {
            return;
        }

        log.info("Data load started");

        if (properties.getSources() == null) {
            log.info("No sources configured");
            return;
        }

        // 2. cada source do YAML e um par arquivo -> colecao
        properties.getSources().forEach(this::importJsonFileToCollection);
    }

    // 3. ler o arquivo do classpath -> converter em documentos -> inserir
    private void importJsonFileToCollection(DataLoadProperties.DataLoadSource source) {
        String rawJson = AlgaShopResourceUtils.readContent(source.getLocation());

        if (StringUtils.isBlank(rawJson)) {
            log.warn("Resource {} is empty or not found", source.getLocation());
            return;
        }

        List<Document> docs = parseJsonToDocuments(rawJson);
        int inserted = insertInto(docs, source.getCollection());
        log.info("{} - Imports: {}/{}", source.getLocation(), inserted, docs.size());
    }

    // 4. BsonArray.parse entende Extended JSON: $uuid, $date, $numberDecimal, $numberLong.
    // e por isso que os arquivos em db/testdata nao usam JSON comum - sem essa notacao
    // um UUID viraria String e um preco viraria Double, e as consultas por tipo nao casariam.
    // falha de parse nao derruba a aplicacao: loga e devolve lista vazia
    private List<Document> parseJsonToDocuments(String rawJson) {
        try {
            BsonArray array = BsonArray.parse(rawJson);
            return array.stream().map(Object::toString).map(Document::parse).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to parse JSON resource {}", e.getMessage(), e);
            return Collections.emptyList();
        }

    }

    // 5. grava Document CRU na colecao - nao passa pelo mapeamento do Spring Data.
    // consequencia: o campo _class precisa estar escrito nos JSONs, senao o Mongo
    // nao sabe em qual classe materializar o documento na hora da leitura.
    private int insertInto(List<Document> mongoDocs, @NotBlank String collectionName) {
        if (mongoDocs == null || mongoDocs.isEmpty()) {
            return 0;
        }

        try {
            // 6. autoDrop APAGA a colecao antes de inserir - garante base limpa
            // no ambiente de estudo, mas destroi dados se ligado onde nao devia
            if (Boolean.TRUE.equals(properties.getAutoDrop())) {
                mongoOperations.getCollection(collectionName).drop();
            }

            return mongoOperations.insert(mongoDocs, collectionName).size();
        } catch (Exception e) {
            log.error("Error inseting documents into {}: {}", collectionName, e.getMessage(), e);
        }

        return 0;
    }
}
