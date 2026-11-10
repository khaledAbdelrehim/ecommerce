package it.example.ecommerce.service;

import it.example.ecommerce.dto.ProductRequest;
import it.example.ecommerce.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse findById(Long id);

    /** Aumenta (delta > 0) o diminuisce (delta < 0) le scorte acquisendo un lock pessimista. */
    ProductResponse adjustStock(Long id, int delta);

    Page<ProductResponse> findAll(Pageable pageable);
}
