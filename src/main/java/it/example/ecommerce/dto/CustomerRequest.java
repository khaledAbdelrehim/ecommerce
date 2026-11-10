package it.example.ecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CustomerRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 100) String cognome,
        @NotNull @Past LocalDate dataNascita,
        @NotBlank
        @Pattern(regexp = "(?i)^[A-Z]{6}\\d{2}[A-EHLMPR-T]\\d{2}[A-Z]\\d{3}[A-Z]$",
                message = "must be a valid codice fiscale")
        String codiceFiscale,
        @NotBlank @Email @Size(max = 255) String email) {
}
