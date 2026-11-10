package it.example.ecommerce.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void givenUsernameWithLineBreaks_whenClean_thenControlCharactersReplaced() {
        String forged = "bob\r\n2026-01-01 INFO Admin logged in";

        assertThat(LogSanitizer.clean(forged)).doesNotContain("\r", "\n").startsWith("bob__");
    }

    @Test
    void givenVeryLongValue_whenClean_thenTruncated() {
        assertThat(LogSanitizer.clean("a".repeat(500))).hasSize(67).endsWith("...");
    }

    @Test
    void givenNull_whenClean_thenNullLiteral() {
        assertThat(LogSanitizer.clean(null)).isEqualTo("null");
    }

    @Test
    void givenPlainUsername_whenClean_thenUnchanged() {
        assertThat(LogSanitizer.clean("mario")).isEqualTo("mario");
    }
}
