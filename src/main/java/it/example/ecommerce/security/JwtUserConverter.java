package it.example.ecommerce.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Converte un JWT convalidato in un'autenticazione la cui identità è {@link AuthenticatedUser}, caricata dal database,
 * così il ruolo e il cliente associato sono sempre aggiornati e i token degli utenti eliminati non sono più validi.
 */
@Component
public class JwtUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserDetailsService userDetailsService;

    public JwtUserConverter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserDetails user = userDetailsService.loadUserByUsername(jwt.getSubject());
        return UsernamePasswordAuthenticationToken.authenticated(user, jwt, user.getAuthorities());
    }
}
