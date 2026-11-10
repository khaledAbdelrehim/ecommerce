package it.example.ecommerce.service;

import it.example.ecommerce.domain.Role;

/**
 * Identifica chi richiede un'operazione, indipendentemente dal canale di trasporto (HTTP, processi pianificati, messaggi).
 * I servizi applicano le regole di autorizzazione aziendali in base a questa identità, coprendo ogni punto di accesso.
 */
public record Requester(Role role, Long customerId) {

    public static Requester admin() {
        return new Requester(Role.ADMIN, null);
    }

    public static Requester customer(Long customerId) {
        return new Requester(Role.CUSTOMER, customerId);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
