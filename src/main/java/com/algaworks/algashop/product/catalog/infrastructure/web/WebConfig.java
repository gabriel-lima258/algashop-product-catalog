package com.algaworks.algashop.product.catalog.infrastructure.web;

import com.algaworks.algashop.product.catalog.application.category.query.CategoryFilter;
import com.algaworks.algashop.product.catalog.application.product.query.ProductFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

// addFormatters e o gancho de conversao da camada web: e aqui que se ensina o Spring MVC
// a transformar o texto cru da query string nos tipos dos campos do command object.
// Sem este registro, ?sortByProperty=salePrice&sortDirection=desc devolve 400 - o
// conversor padrao para enum e um valueOf, que diferencia caixa e so entende o nome da
// constante Java (SALE_PRICE), nao o do campo no documento (salePrice), que e justamente
// o que o contrato OpenAPI publica e o que o cliente escreve.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // a direcao usa o mesmo resolve dos demais: o nome da constante ja e o que o
        // cliente escreve, so muda a caixa
        registry.addConverter(String.class, Sort.Direction.class,
                source -> resolve(Sort.Direction.values(), source,
                        direction -> direction.name().toLowerCase(Locale.ROOT)));

        // uma linha por enum ordenavel. ATENCAO: filtro ordenavel novo precisa entrar aqui,
        // senao volta o 400 - o preco de nao ter um ConverterFactory generico
        registry.addConverter(String.class, ProductFilter.SortType.class,
                source -> resolve(ProductFilter.SortType.values(), source,
                        ProductFilter.SortType::getPropertyName));

        registry.addConverter(String.class, CategoryFilter.SortType.class,
                source -> resolve(CategoryFilter.SortType.values(), source,
                        CategoryFilter.SortType::getPropertyName));
    }

    // aceita os dois nomes da constante, ignorando caixa: "salePrice" (campo no documento)
    // e "SALE_PRICE" (constante Java). valor fora da lista lanca, e a allowlist do enum
    // segue valendo.
    // a mensagem lista os valores aceitos a partir do proprio enum - e por isso que o
    // ApiExceptionHandler consegue devolver um 400 legivel sem nenhum arquivo de mensagens,
    // e por isso que ela nunca desatualiza quando um valor novo entra no enum
    private <T extends Enum<T>> T resolve(T[] values, String source, Function<T, String> propertyName) {
        String value = source.trim();

        return Arrays.stream(values)
                .filter(constant -> value.equalsIgnoreCase(propertyName.apply(constant))
                        || value.equalsIgnoreCase(constant.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid value '%s'; must be one of: %s".formatted(value,
                                Arrays.stream(values).map(propertyName).collect(Collectors.joining(", ")))));
    }
}
