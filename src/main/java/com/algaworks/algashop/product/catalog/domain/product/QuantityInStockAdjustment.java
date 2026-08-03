package com.algaworks.algashop.product.catalog.domain.product;

import java.util.UUID;

// PORTA DE SAIDA no dominio: o StockService declara "preciso somar/subtrair estoque de
// forma segura" sem saber que existe MongoDB, findAndModify ou $inc do outro lado.
// A implementacao vive em infrastructure/persistence/product.
//
// Repare que a porta trabalha com UUID e int, nao com Product: ela nao carrega o agregado,
// justamente porque carregar seria abrir a janela de concorrencia que este desenho evita
public interface QuantityInStockAdjustment {
    Result increase(UUID productId, int quantity);
    Result decrease(UUID productId, int quantity);

    // Devolve o ANTES e o DEPOIS, e nao void, porque quem chama precisa saber se houve
    // TRANSICAO - e transicao nao da para deduzir olhando so o estado final.
    // Os dois valores tem que vir da MESMA operacao atomica, senao o "antes" e um palpite
    record Result(
            UUID productId,
            int previousQuantity,
            int newQuantity
    ) {
        // "acabou o estoque" e diferente de "esta zerado": o segundo e verdade a cada
        // consulta enquanto ninguem repoe, e viraria uma enxurrada de eventos iguais.
        // A condicao previousQuantity != 0 e o que transforma estado em acontecimento
        public boolean isOutOfStock() {
            return newQuantity == 0 && previousQuantity != 0;
        }

        // mesma logica ao contrario: so e reposicao se ANTES estava zerado.
        // repor 10 sobre 40 nao e novidade para ninguem
        public boolean isRestocked() {
            return newQuantity > 0 && previousQuantity == 0;
        }
    }
}
