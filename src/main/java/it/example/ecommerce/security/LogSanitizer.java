package it.example.ecommerce.security;

/** Neutralizza i valori forniti dal client prima di inserirli nei log (falsificazione dei log). */
public final class LogSanitizer {

    private static final int MAX_LENGTH = 64;

    private LogSanitizer() {
    }

    /** Sostituisce ogni carattere di controllo (CR, LF, tab, ...) con '_' e tronca i valori troppo lunghi. */
    public static String clean(String value) {
        if (value == null) {
            return "null";
        }
        String truncated = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) + "..." : value;
        return truncated.replaceAll("\\p{Cntrl}", "_");
    }
}
