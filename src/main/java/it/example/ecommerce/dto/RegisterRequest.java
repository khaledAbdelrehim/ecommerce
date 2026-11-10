package it.example.ecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Dati per la registrazione autonoma. Il ruolo non viene mai accettato dal client: i nuovi account sono sempre CUSTOMER. */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
        @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 100) String cognome,
        @NotNull @Past LocalDate dataNascita,
        @NotBlank
        @Pattern(regexp = "(?i)^[A-Z]{6}\\d{2}[A-EHLMPR-T]\\d{2}[A-Z]\\d{3}[A-Z]$",
                message = "must be a valid codice fiscale")
        String codiceFiscale,
        @NotBlank @Email @Size(max = 255) String email) {

    public CustomerRequest toCustomerRequest() {
        return new CustomerRequest(nome, cognome, dataNascita, codiceFiscale, email);
    }
}
