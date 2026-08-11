package com.algaworks.algashop.product.catalog.application.product.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// =============================================================================
// POR QUE implements Serializable?
// =============================================================================
// O RedisCacheConfig monta o cache a partir de RedisCacheConfiguration
// .defaultCacheConfig(), e o serializador de VALORES desse default é o
// RedisSerializer.java(...) — ou seja, a serialização nativa do Java. O objeto
// vai para o Redis como um array de bytes produzido pelo ObjectOutputStream.
//
// Sem "implements Serializable", a primeira gravação no cache estoura com
// NotSerializableException — em runtime, não em compilação.
//
// A EXIGÊNCIA É TRANSITIVA: todo campo também precisa ser serializável. Os
// tipos da JDK usados aqui (UUID, OffsetDateTime, BigDecimal, String, Boolean,
// Integer, Long) já são. O único tipo nosso é o CategoryMinimalOutput, que
// também implementa Serializable — se alguém remover de lá, quebra aqui.
// =============================================================================

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductDetailOutput implements Serializable {
    private UUID id;
    private OffsetDateTime addedAt;
    private String name;
    private String brand;
    private BigDecimal regularPrice;
    private BigDecimal salePrice;
    private Boolean inStock;
    private Boolean enabled;
    private CategoryMinimalOutput category;
    private String description;

    private String slug;
    private Boolean hasDiscount;

    private Integer quantityInStock;
    private Integer discountPercentageRounded;

    private Long version;
    private OffsetDateTime updatedAt;

    private ImageOutput mainImage;
}
