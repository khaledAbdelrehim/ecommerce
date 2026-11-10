package it.example.ecommerce.mapper;

import it.example.ecommerce.domain.Product;
import it.example.ecommerce.dto.ProductRequest;
import it.example.ecommerce.dto.ProductResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    Product toEntity(ProductRequest request);

    ProductResponse toResponse(Product product);
}
