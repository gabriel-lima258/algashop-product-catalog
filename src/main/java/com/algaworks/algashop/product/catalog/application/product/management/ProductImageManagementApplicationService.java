package com.algaworks.algashop.product.catalog.application.product.management;

import com.algaworks.algashop.product.catalog.application.product.query.ImageOutput;
import com.algaworks.algashop.product.catalog.application.storage.StorageProvider;
import com.algaworks.algashop.product.catalog.application.util.CacheNames;
import com.algaworks.algashop.product.catalog.application.util.Mapper;
import com.algaworks.algashop.product.catalog.domain.DomainException;
import com.algaworks.algashop.product.catalog.domain.product.Image;
import com.algaworks.algashop.product.catalog.domain.product.Product;
import com.algaworks.algashop.product.catalog.domain.product.ProductNotFoundException;
import com.algaworks.algashop.product.catalog.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageManagementApplicationService {

    private final ProductRepository productRepository;
    private final StorageProvider storageProvider;
    private final Mapper mapper;

    // cache é apagado e renovado de produto toda vez que alterar imagem
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public ImageOutput create(UUID productId, ImageInput input) {
        // valida campos
        Objects.requireNonNull(productId);
        Objects.requireNonNull(input);

        // procura produto
        Product product = findProduct(productId);

        // A conferencia que sustenta o desenho de duas fases: como o arquivo nao passou
        // por aqui, o catalogo nao tem como saber que ele existe - precisa PERGUNTAR ao
        // provedor. Sem isto, um cliente que pediu a URL e nunca enviou o arquivo (ou
        // deixou a assinatura expirar) grava no produto uma referencia para imagem que
        // nao existe, e o erro so aparece no navegador de quem abrir a pagina.
        if (!storageProvider.fileExists(input.getRemoteFileName())) {
            throw new DomainException(String.format("Image %s was not found on storage provider", input.getRemoteFileName()));
        }
        if (productRepository.existsByImagesName(input.getRemoteFileName())) {
            throw new DomainException(String.format("Image %s is already in use", input.getRemoteFileName()));
        }

        // adiciona uma imagem e gera um UUID e salva em repository
        UUID imageId = product.addImage(input.getRemoteFileName());
        productRepository.save(product);

        // busca imagem em produtos e devolve em dto
        Image image = findImage(product, imageId);

        return mapper.convert(image, ImageOutput.class);
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public void delete(UUID productId, UUID imageId) {
        // valida campos
        Objects.requireNonNull(productId);
        Objects.requireNonNull(imageId);

        // procura produto e imagem
        Product product = findProduct(productId);
        Image image = findImage(product, imageId);

        // remove imagem de produtos
        product.removeImage(imageId);
        // remove imagem do provider
        storageProvider.deleteFile(image.getName());

        // salva em repository
        productRepository.save(product);
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#productId")
    public void primaryImage(UUID productId, UUID imageId) {
        // valida campos
        Objects.requireNonNull(productId);
        Objects.requireNonNull(imageId);

        // procura produto e imagem
        Product product = findProduct(productId);
        product.changeMainImage(imageId);

        // salva em repository
        productRepository.save(product);
    }

    private Product findProduct(UUID productId) {
        // procura produto
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Image findImage(Product product, UUID imageId) {
        return product.getImage(imageId).orElseThrow(() ->
                new DomainException(String.format("Image of id %s was not found on product %s", imageId, product.getId()))
        );
    }

}
