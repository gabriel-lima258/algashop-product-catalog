package com.algaworks.algashop.product.catalog.application.category.query;

import com.algaworks.algashop.product.catalog.application.util.SortablePageFilter;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.domain.Sort;

// Command object: o Spring binda a query string direto nestes campos
// (GET /api/v1/categories?enabled=true&name=note&size=20), sem precisar de @RequestParam.
// Herda page/size de PageFilter e sortByProperty/sortDirection de SortablePageFilter.
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class CategoryFilter extends SortablePageFilter<CategoryFilter.SortType> {

    private String name;    // busca por regex, casa em qualquer parte do nome
    private Boolean enabled;

    // usa o que o cliente mandou; se veio vazio, cai no default
    @Override
    public CategoryFilter.SortType getSortByPropertyOrDefault() {
        return getSortByProperty() != null ? getSortByProperty() : CategoryFilter.SortType.NAME;
    }

    @Override
    public Sort.Direction getSortDirectionOrDefault() {
        return getSortDirection() != null ? getSortDirection() : Sort.Direction.ASC;
    }

    // Decide se ESTA consulta entra em cache - e a resposta e "so se for exatamente a
    // consulta padrao". Usado em dois lugares: no condition do @Cacheable (cache do
    // servidor, no Redis) e no CategoryController (cabecalhos HTTP, cache do cliente).
    //
    // Por que tao restritivo: cachear listagem com filtro livre e armadilha de
    // cardinalidade. Cada combinacao de nome, enabled, pagina, tamanho e ordenacao vira
    // uma chave propria; com poucos parametros ja sao milhares de combinacoes possiveis,
    // quase todas pedidas uma vez so. O Redis enche de entradas que nunca sao lidas de
    // novo, a memoria acaba, o allkeys-lru comeca a despejar - e despeja tambem o que
    // era util. Taxa de acerto perto de zero, custo real.
    //
    // O filtro default e o oposto disso: e o que a home pede, e o que quase toda visita
    // faz sem tocar em nada. Uma chave so, com reuso altissimo. Cache paga quando ha
    // reuso; a regra aqui e simplesmente nao cachear onde nao ha.
    public boolean isCacheable() {
        return isDefaultFilter();
    }

    // Compara por equals(), que o @Data + @EqualsAndHashCode(callSuper = true) geram ao
    // longo dos tres niveis da hierarquia. Entao "?enabled=true&page=0" casa com o
    // default, e "?page=1" nao
    private boolean isDefaultFilter() {
        return this.equals(defaultFilter());
    }

    // @SuperBuilder, e nao @Builder, existe por causa desta chamada: o builder normal do
    // Lombok so enxerga os campos da propria classe, e page/size vem de PageFilter,
    // sortByProperty/sortDirection vem de SortablePageFilter. Sem o SuperBuilder nao ha
    // como montar o filtro default numa hierarquia de tres niveis
    public static CategoryFilter defaultFilter() {
        return CategoryFilter.builder()
                .name(null)
                .enabled(true)
                .page(0)
                .size(15)
                .sortDirection(Sort.Direction.ASC)
                .sortByProperty(SortType.NAME)
                .build();
    }

    // enum em vez de String solta: o cliente so consegue ordenar pelos campos
    // listados aqui, entao nao da pra pedir sort por campo inexistente
    @Getter
    @RequiredArgsConstructor
    public enum SortType {
        NAME("name");

        private final String propertyName;
    }
}
