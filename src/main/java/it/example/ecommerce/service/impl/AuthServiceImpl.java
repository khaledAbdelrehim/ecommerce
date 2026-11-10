package it.example.ecommerce.service.impl;

import it.example.ecommerce.domain.AppUser;
import it.example.ecommerce.domain.Role;
import it.example.ecommerce.dto.CustomerResponse;
import it.example.ecommerce.dto.LoginRequest;
import it.example.ecommerce.dto.RegisterRequest;
import it.example.ecommerce.dto.TokenResponse;
import it.example.ecommerce.exception.DuplicateResourceException;
import it.example.ecommerce.repository.AppUserRepository;
import it.example.ecommerce.repository.CustomerRepository;
import it.example.ecommerce.security.AuthenticatedUser;
import it.example.ecommerce.security.LogSanitizer;
import it.example.ecommerce.security.TokenService;
import it.example.ecommerce.service.AuthService;
import it.example.ecommerce.service.CustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthServiceImpl(CustomerService customerService, CustomerRepository customerRepository,
                           AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager, TokenService tokenService) {
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @Override
    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        // generic message: a public endpoint must not reveal which identifier is already registered
        DuplicateResourceException conflict = new DuplicateResourceException(
                "Registration failed: username, codice fiscale or email already in use");
        String username = AppUser.normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw conflict;
        }
        CustomerResponse created;
        try {
            created = customerService.create(request.toCustomerRequest());
        } catch (DuplicateResourceException e) {
            throw conflict;
        }
        userRepository.save(AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER)
                .customer(customerRepository.getReferenceById(created.id()))
                .build());
        log.info("User {} registered (customerId={})", LogSanitizer.clean(request.username()), created.id());
        return created;
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
            log.info("User {} logged in", LogSanitizer.clean(request.username()));
            return tokenService.issue((AuthenticatedUser) authentication.getPrincipal());
        } catch (AuthenticationException e) {
            log.warn("Failed login attempt for user {}", LogSanitizer.clean(request.username()));
            throw e;
        }
    }
}
