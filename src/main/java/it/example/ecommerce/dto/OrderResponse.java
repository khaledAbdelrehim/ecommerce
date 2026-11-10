package it.example.ecommerce.dto;

import it.example.ecommerce.domain.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        OrderStatus stato,
        Instant dataCreazione,
        List<OrderItemResponse> righe) {
}
