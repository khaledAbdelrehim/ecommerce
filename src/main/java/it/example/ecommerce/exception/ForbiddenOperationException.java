package it.example.ecommerce.exception;

/** Il richiedente non è autorizzato a eseguire l'operazione (risposta 403); è indipendente da Spring Security. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
