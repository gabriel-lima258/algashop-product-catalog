package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.util.IdGenerator;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

// O extrato do estoque: uma linha por entrada ou saida, escrita junto com o ajuste.
//
// Por que ele existe: a Fase 13 deixou o saldo correto e sem historia nenhuma. Saldo certo
// que ninguem consegue explicar e pior que saldo errado - com o errado voce ao menos sabe
// que ha um problema. O ajuste diz QUANTO tem; o movimento diz COMO chegou ali.
//
// Repare no contraste deliberado com o Product, que fica no mesmo pacote:
//   - NAO estende AbstractAggregateRoot. Nao ha evento para registrar aqui: o fato ja
//     aconteceu, e quem o anuncia e o StockService
//   - NAO tem @Version. Lock otimista protege alguem de sobrescrever alteracao alheia, e
//     este documento nunca e alterado - so nasce
//   - NAO tem auditoria (@CreatedDate/@LastModifiedDate). O occurredAt e preenchido no
//     construtor porque "quando o fato ocorreu" e o dado em si, nao metadado sobre a linha
// Nada disso e esquecimento: um registro imutavel de fato consumado nao tem invariante
// para proteger. Toda a maquinaria do agregado existe para defender regra que muda de
// estado, e aqui nao ha estado que mude.
//
// movementQuantity e SEMPRE positivo - o sinal vive no type. A alternativa (guardar -10
// para saida) economiza um campo e cobra caro na leitura: toda soma passa a depender de
// ninguem ter errado o sinal na escrita. Com o type explicito, "quanto saiu no mes" e um
// filtro, nao uma convencao que alguem precisa lembrar.
@Document(collection = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StockMovement {

    // onlyExplicitlyIncluded exige que ALGUEM seja incluido - sem este @Include todo
    // movimento seria igual a todo outro, e um Set de movimentos guardaria exatamente um.
    // Identidade por id, como no Product: dois movimentos com os mesmos numeros continuam
    // sendo dois fatos distintos
    @EqualsAndHashCode.Include
    private UUID id;

    private OffsetDateTime occurredAt;

    // A consulta obvia desta colecao e "o historico deste produto". Sem indice isso e
    // varredura da colecao inteira, e ela so cresce - nada aqui e apagado nem atualizado
    @Indexed
    private UUID productId;

    private Integer movementQuantity;
    private Integer previousQuantity;
    private Integer newQuantity;
    private MovementType type;

    // @Builder no construtor, e nao na classe: id e occurredAt ficam de fora do builder de
    // proposito. Quem registra um movimento nao escolhe quando ele aconteceu
    @Builder
    public StockMovement(UUID productId, Integer movementQuantity, Integer previousQuantity, Integer newQuantity, MovementType type) {
        this.id = IdGenerator.generateTimeBasedUUID();
        this.occurredAt = OffsetDateTime.now();
        this.productId = productId;
        this.movementQuantity = movementQuantity;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.type = type;
    }

    public enum MovementType {
        STOCK_IN,
        STOCK_OUT
    }
}
