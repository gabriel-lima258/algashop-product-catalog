package com.algaworks.algashop.product.catalog.domain.product;

import com.algaworks.algashop.product.catalog.domain.util.IdGenerator;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.UUID;

// Identidade por id, e SO por id: duas imagens com o mesmo nome de arquivo continuam
// sendo objetos diferentes. Quem impede o mesmo arquivo de ser anexado duas vezes e o
// existsByImagesName do repositorio, nao o Set - e essa separacao e proposital, porque
// a regra "um arquivo pertence a um produto so" e do catalogo inteiro, nao deste agregado.
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image {

    @EqualsAndHashCode.Include
    private UUID id;

    private String name;

    Image(String name) {
        this(IdGenerator.generateTimeBasedUUID(), name);
    }

    public Image(UUID id, String name) {
        Objects.requireNonNull(id);

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException();
        }

        this.id = id;
        this.name = name;
    }
}
