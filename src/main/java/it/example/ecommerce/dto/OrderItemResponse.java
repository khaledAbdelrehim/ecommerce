package it.example.ecommerce.dto;

public record OrderItemResponse(Long productId, String productCodice, String productNome, int quantita) {
}
