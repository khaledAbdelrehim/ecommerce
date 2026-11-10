package it.example.ecommerce.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productCode, int requested, int available) {
        super("Insufficient stock for product %s: requested %d, available %d"
                .formatted(productCode, requested, available));
    }
}
