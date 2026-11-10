package it.example.ecommerce.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("customerSecurity")
public class CustomerSecurity {

    public boolean isSelf(Long customerId, Authentication authentication) {
        return authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.getCustomerId() != null
                && user.getCustomerId().equals(customerId);
    }
}
