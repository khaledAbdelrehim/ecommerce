package it.example.ecommerce.service;

import it.example.ecommerce.dto.CustomerResponse;
import it.example.ecommerce.dto.LoginRequest;
import it.example.ecommerce.dto.RegisterRequest;
import it.example.ecommerce.dto.TokenResponse;

public interface AuthService {

    /** Crea in modo atomico un cliente e il relativo utente CUSTOMER. */
    CustomerResponse register(RegisterRequest request);

    /** Verifica le credenziali e restituisce un token bearer. */
    TokenResponse login(LoginRequest request);
}
