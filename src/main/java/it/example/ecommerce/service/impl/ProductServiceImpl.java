package it.example.ecommerce.service.impl;

import it.example.ecommerce.domain.Product;
import it.example.ecommerce.dto.ProductRequest;
import it.example.ecommerce.dto.ProductResponse;
import it.example.ecommerce.exception.DuplicateResourceException;
import it.example.ecommerce.exception.ResourceNotFoundException;
import it.example.ecommerce.mapper.ProductMapper;
import it.example.ecommerce.repository.ProductRepository;
import it.example.ecommerce.service.PageableRules;
import it.example.ecommerce.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        // the Product constructor normalizes the codice: the duplicate check uses the stored form
        Product candidate = productMapper.toEntity(request);
        if (productRepository.existsByCodice(candidate.getCodice())) {
            throw new DuplicateResourceException("Product with codice " + candidate.getCodice() + " already exists");
        }
        Product product = productRepository.save(candidate);
        log.info("Product {} created (id={}, stock={})", product.getCodice(), product.getId(), product.getStock());
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse adjustStock(Long id, int delta) {
        Product product = productRepository.findAllByIdForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.adjustStock(delta);
        log.info("Stock of product {} adjusted by {} (new stock={})", id, delta, product.getStock());
        return productMapper.toResponse(product);
    }

    @Override
    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private static final Set<String> SORTABLE = Set.of("id", "codice", "nome", "stock");

    @Override
    public Page<ProductResponse> findAll(Pageable pageable) {
        return productRepository.findAll(PageableRules.restrict(pageable, SORTABLE)).map(productMapper::toResponse);
    }
}
