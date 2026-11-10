package it.example.ecommerce.mapper;

import it.example.ecommerce.domain.Order;
import it.example.ecommerce.domain.OrderItem;
import it.example.ecommerce.dto.OrderItemResponse;
import it.example.ecommerce.dto.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "customerId", source = "customer.id")
    OrderResponse toResponse(Order order);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productCodice", source = "product.codice")
    @Mapping(target = "productNome", source = "product.nome")
    OrderItemResponse toResponse(OrderItem item);
}
