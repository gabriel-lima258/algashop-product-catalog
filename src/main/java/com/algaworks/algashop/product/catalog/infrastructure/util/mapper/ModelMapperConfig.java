package com.algaworks.algashop.product.catalog.infrastructure.util.mapper;

import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;
import com.algaworks.algashop.product.catalog.application.product.query.ProductSummaryOutput;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.application.util.Slugfier;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.convention.NamingConventions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    // convertor de texto para gerar slug de textos
    private final Converter<String, String> fromStringToSlugConverter = ctx ->
            Slugfier.slugify(ctx.getSource());

    // conversor de texto para resumir grandes textos.
    // sem uso desde que a listagem virou aggregation - quem abrevia o shortDescription agora
    // e o $substrCP do $project. mantido como referencia do que o mapper fazia:
    // repare que abbreviate corta em 15 e acrescenta "...", enquanto o $substrCP corta em
    // 50 caracteres crus - o resumo mudou de formato junto com a mudanca de estrategia
    private final Converter<String, String> fromStringToShortStringConverter = ctx ->
            StringUtils.abbreviate(ctx.getSource(), 15);

    @Bean
    public Mapper mapper() {
        ModelMapper modelMapper = new ModelMapper();
        configuration(modelMapper);
        return modelMapper::map;
    }

    // configurando o mapper para entender a mapear o objeto sem precisar ser exatamente como
    // o padrão java Bean como get e setter, strategy indica como deve ser mapeado os objetos
    // ou seja, STRICT indica que o nome dos dois DEVEM ser iguais
    private void configuration(ModelMapper modelMapper) {
        modelMapper.getConfiguration()
                .setSourceNamingConvention(NamingConventions.NONE)
                .setDestinationNamingConvention(NamingConventions.NONE)
                .setMatchingStrategy(MatchingStrategies.STRICT);

        // alocando o conversor de slug do nome produto ao dto slug.
        // so o Detail tem TypeMap: o findById continua sendo find() + mapeamento em Java.
        // o TypeMap do ProductSummaryOutput foi REMOVIDO porque a listagem virou aggregation -
        // quem recorta e converte agora e o $project, do lado do banco. os dois caminhos
        // convivem de proposito, e essa e a diferenca pratica entre eles
        modelMapper.createTypeMap(Product.class, ProductDetailOutput.class)
                .addMappings(mappings -> {
                    mappings.using(fromStringToSlugConverter)
                            .map(Product::getName, ProductDetailOutput::setSlug);
                });

    }
}
