package com.algaworks.algashop.product.catalog.infrastructure.util.mapper;

import com.algaworks.algashop.product.catalog.application.product.query.ImageOutput;
import com.algaworks.algashop.product.catalog.application.product.query.ProductDetailOutput;
import com.algaworks.algashop.product.catalog.application.product.query.ProductSummaryOutput;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.application.util.Slugfier;
import com.algaworks.algashop.product.catalog.domain.product.Image;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.convention.NamingConventions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelMapperConfig {

    @Autowired
    private ApplicationMappingProperty applicationMappingProperty;

    // convertor de texto para gerar slug de textos
    private final Converter<String, String> fromStringToSlugConverter = ctx ->
            Slugfier.slugify(ctx.getSource());

    // conversor de texto para resumir grandes textos.
    // repare que abbreviate corta em 15 e acrescenta "...", enquanto o $substrCP corta em
    // 50 caracteres crus - o resumo mudou de formato junto com a mudanca de estrategia
    private final Converter<String, String> fromStringToShortStringConverter = ctx ->
            StringUtils.abbreviate(ctx.getSource(), 15);

    private final Converter<String, String> fromFileNameToUrlConverter = ctx ->
            convertFromFileNameToUrl(ctx.getSource());

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

        // convertendo nome arquivo url em image para saida dto
        modelMapper.createTypeMap(Image.class, ImageOutput.class)
                .addMappings(mapper -> mapper.using(fromFileNameToUrlConverter)
                        .map(Image::getName, ImageOutput::setUrl));

        modelMapper.createTypeMap(Product.class, ProductSummaryOutput.class)
                        .addMappings(mapper -> mapper.using(fromStringToShortStringConverter)
                                .map(Product::getDescription, ProductSummaryOutput::setShortDescription));

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

    private String convertFromFileNameToUrl(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return null;
        }

        String imageStorageUrl = applicationMappingProperty.getImageStorageUrl();
        return imageStorageUrl + "/" + fileName;
    }
}
