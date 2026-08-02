package com.algaworks.algashop.product.catalog.application.category.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

// Evento de APLICACAO, nao de dominio - e a diferenca importa.
// Os eventos do Product nascem dentro do agregado, que decide sozinho que houve um fato.
// Este e publicado a mao pelo CategoryManagementApplicationService, porque a Category nao
// tem nada a ver com o assunto: quem tem interesse em saber que ela mudou e o catalogo de
// PRODUTOS, que guarda uma copia dela. Fazer a Category emitir o evento seria dar a ela
// conhecimento de um problema que e do vizinho.
//
// Leva name e enabled, e nao so o id, para o consumidor nao precisar reler a categoria
// que acabou de ser gravada. O payload e exatamente o que o ProductCategory copia -
// crescer um sem crescer o outro nao faz sentido
@Getter
@AllArgsConstructor
public class CategoryUpdatedEvent {
    private UUID categoryId;
    private String name;
    private Boolean enabled;
}
