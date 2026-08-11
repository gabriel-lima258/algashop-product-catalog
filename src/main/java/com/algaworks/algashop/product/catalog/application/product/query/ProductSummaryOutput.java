package com.algaworks.algashop.product.catalog.application.product.query;

import com.algaworks.algashop.product.catalog.application.util.Slugfier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// Destino DIRETO do $project do aggregation pipeline: o Mongo devolve os documentos ja
// com estes nomes de campo, sem ModelMapper no meio. Por isso o TypeMap deste DTO saiu
// do ModelMapperConfig - quem "mapeia" agora e a projecao, do lado do banco.
// O ProductDetailOutput continua no caminho antigo (find + mapper), de proposito.
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductSummaryOutput {
    private UUID id;
    private OffsetDateTime addedAt;
    private String name;
    private String brand;
    private BigDecimal regularPrice;
    private BigDecimal salePrice;
    private Boolean inStock;
    private Boolean enabled;
    // vem embutido no proprio documento de produto. ja foi preenchido pelo $lookup +
    // $unwind do pipeline, e antes disso pelo @DocumentReference, ao custo de um N+1
    private CategoryMinimalOutput category;

    private String shortDescription;

    private Boolean hasDiscount;

    private Integer quantityInStock;
    private Integer discountPercentageRounded;

    // pontuação de buscas textuais, do mais relevante ao menor
    private Float score;

    private ImageOutput mainImage;

    // slug derivado em Java, na hora de serializar - o Jackson chama este getter e publica
    // o campo "slug" no JSON, sem ele existir na classe nem no documento.
    // ficou de fora do $project de proposito: tirar acento no Mongo exigiria uma cadeia de
    // $replaceAll por caractere, e aqui e uma linha (ver Slugfier)
    public String getSlug() {
        return Slugfier.slugify(this.getName());
    }
}
