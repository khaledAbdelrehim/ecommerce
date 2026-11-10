package it.example.ecommerce.domain;

import it.example.ecommerce.exception.InsufficientStockException;
import it.example.ecommerce.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.Locale;

/** La dimensione del lotto consente a Hibernate di caricare con un'unica query IN i prodotti di più righe d'ordine a caricamento differito. */
@Entity
@Table(name = "products")
@BatchSize(size = 50)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codice;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private int stock;

    @Version
    private long version;

    @Builder
    public Product(String codice, String nome, int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("stock must be >= 0");
        }
        this.codice = codice == null ? null : codice.trim().toUpperCase(Locale.ROOT);
        this.nome = nome;
        this.stock = stock;
    }

    public boolean hasStockFor(int quantity) {
        return stock >= quantity;
    }

    public void decreaseStock(int quantity) {
        requirePositive(quantity);
        if (!hasStockFor(quantity)) {
            throw new InsufficientStockException(codice, quantity, stock);
        }
        stock -= quantity;
    }

    public void increaseStock(int quantity) {
        requirePositive(quantity);
        if ((long) stock + quantity > Integer.MAX_VALUE) {
            throw new InvalidRequestException("Stock would exceed the maximum allowed value");
        }
        stock += quantity;
    }

    /** Applica una variazione con segno: un valore positivo aumenta le scorte, uno negativo le diminuisce (mai sotto zero). */
    public void adjustStock(int delta) {
        if (delta > 0) {
            increaseStock(delta);
        } else if (delta < 0) {
            decreaseStock(-delta);
        } else {
            throw new InvalidRequestException("delta must not be zero");
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
    }
}
