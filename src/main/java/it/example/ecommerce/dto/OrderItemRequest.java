package it.example.ecommerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(OrderItemRequest.MAX_QUANTITY) Integer quantita) {

    public static final int MAX_QUANTITY = 10_000;
}
