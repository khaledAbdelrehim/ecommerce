package it.example.ecommerce.domain;

import it.example.ecommerce.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppUserIT extends IntegrationTestBase {

    @Test
    void givenCustomerRoleWithoutCustomer_whenBuild_thenRejected() {
        assertThatThrownBy(() -> AppUser.builder().username("x").password("p").role(Role.CUSTOMER).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenCustomerAlreadyLinked_whenSecondUserLinksSameCustomer_thenConstraintViolation() {
        AppUser second = AppUser.builder().username("alice2").password("p").role(Role.CUSTOMER)
                .customer(alice).build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givenAdminWithoutCustomer_whenSave_thenAccepted() {
        assertThat(userRepository.findByUsername("admin")).isPresent();
    }
}
