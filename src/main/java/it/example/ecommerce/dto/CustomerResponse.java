package it.example.ecommerce.dto;

import java.time.LocalDate;

public record CustomerResponse(
        Long id,
        String nome,
        String cognome,
        LocalDate dataNascita,
        String codiceFiscale,
        String email) {
}
