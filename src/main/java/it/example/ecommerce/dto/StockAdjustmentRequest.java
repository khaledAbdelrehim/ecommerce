package it.example.ecommerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Variazione delle scorte con segno: un valore positivo le aumenta, uno negativo le diminuisce. Zero viene rifiutato perché privo di effetto. */
public record StockAdjustmentRequest(
        @NotNull @Min(-1_000_000) @Max(1_000_000) Integer delta) {
}
