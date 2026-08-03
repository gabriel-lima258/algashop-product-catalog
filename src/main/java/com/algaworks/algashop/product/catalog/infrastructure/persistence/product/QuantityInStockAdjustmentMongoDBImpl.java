package com.algaworks.algashop.product.catalog.infrastructure.persistence.product;

import com.algaworks.algashop.product.catalog.domain.product.InsufficientStockException;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import com.algaworks.algashop.product.catalog.domain.product.ProductNotFoundException;
import com.algaworks.algashop.product.catalog.domain.product.QuantityInStockAdjustment;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

// O adaptador que faz o estoque mudar SEM carregar o agregado.
//
// A tecnica e a atualizacao condicional: o filtro do findAndModify carrega a regra de
// negocio junto. O Mongo avalia o filtro e aplica o update numa operacao unica e
// indivisivel, no servidor - nao ha janela entre "conferir" e "gravar" para outra thread
// se meter. E o mesmo raciocinio de um compare-and-set.
//
// Por que nao o caminho obvio (findById -> if -> save): entre a leitura e a gravacao cabe
// a operacao inteira de outra requisicao. Dois clientes leem estoque 1, os dois aprovam o
// saque, os dois gravam 0 - e sairam duas unidades de um estoque que tinha uma.
// Ver docs/02-persistencia/concorrencia-e-atomicidade.md
@Component
@RequiredArgsConstructor
public class QuantityInStockAdjustmentMongoDBImpl implements QuantityInStockAdjustment {

    private final MongoOperations mongoOperations;

    @Override
    public Result increase(UUID productId, int quantity) {
        // entrada nao tem teto: qualquer produto existente aceita reposicao
        Query query = Query.query(byId(productId));
        return changeStockQuantity(productId, quantity, query);
    }

    @Override
    public Result decrease(UUID productId, int quantity) {
        // AQUI mora a corretude: a condicao "tem saldo" vai DENTRO do filtro, e nao num
        // if em Java. E o proprio Mongo que garante, ao casar o documento, que ninguem
        // decrementou no meio do caminho. Sem saldo, o filtro nao casa e nada acontece.
        //
        // Repare que o $inc sozinho NAO protegeria: ele soma o delta ao que estiver la,
        // inclusive levando o estoque a numero negativo. Quem impede o negativo e o filtro
        Query query = Query.query(byId(productId)
                .and("quantityInStock").gte(quantity));

        // saque e entrada com sinal trocado - o mesmo $inc serve para os dois
        return changeStockQuantity(productId, quantity * -1, query);
    }

    private Result changeStockQuantity(UUID productId, int delta, Query queryForUpdate) {
        Update update = new Update()
                .inc("quantityInStock", delta)
                // incremento EXPLICITO da versao. O Spring Data faria isso sozinho
                // (QueryOperations$UpdateContext.increaseVersionForUpdateIfNecessary), mas
                // so quando o update ainda nao mexe no campo - ou seja, esta linha e o que
                // faz o framework pular a dele. O efeito e o mesmo; o que se ganha e a
                // versao subir por decisao escrita, e nao por efeito colateral.
                // Sem ela, um save() concorrente vindo de outro caminho nao perceberia que
                // o documento mudou, e sobrescreveria o estoque em silencio
                .inc("version", 1)
                .set("updatedAt", OffsetDateTime.now());

        // returnNew(false): devolve o documento COMO ERA antes da alteracao, na MESMA
        // operacao que o alterou. E o detalhe que fecha a atomicidade de ponta a ponta -
        // ler o valor anterior numa consulta separada, antes daqui, deixava justamente o
        // "antes" desprotegido, e era ele que decidia se o evento saia
        Product before = mongoOperations.findAndModify(queryForUpdate, update,
                new FindAndModifyOptions().returnNew(false), Product.class);

        if (before == null) {
            // nao casou. Duas causas possiveis, e elas viram respostas HTTP diferentes
            throw reasonForNoMatch(productId, delta);
        }

        int previousQuantity = before.getQuantityInStock();

        // o novo valor e calculado, nao relido: o $inc foi aplicado exatamente sobre o
        // documento devolvido acima, entao previous + delta E o que ficou gravado.
        // Uma segunda leitura para "conferir" reabriria a janela que o returnNew fechou
        return new Result(productId, previousQuantity, previousQuantity + delta);
    }

    // so roda no caminho de falha, que ja e excepcional - o caminho feliz continua com
    // uma unica ida ao banco
    private RuntimeException reasonForNoMatch(UUID productId, int delta) {
        Product product = mongoOperations.findOne(Query.query(byId(productId)), Product.class);

        if (product == null) {
            return new ProductNotFoundException(productId);
        }

        // o produto existe, entao o que barrou foi a clausula de saldo do decrease
        return new InsufficientStockException(productId, Math.abs(delta), product.getQuantityInStock());
    }

    // "id" e o nome da propriedade em Java; o QueryMapper traduz para "_id" antes de
    // enviar - a mesma traducao que acontece com o category.id do indice composto
    private Criteria byId(UUID productId) {
        return Criteria.where("id").is(productId);
    }
}
