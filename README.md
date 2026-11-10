# E-Commerce API

API REST per clienti, prodotti e ordini con gestione transazionale dello stock, realizzata con Java 21 e Spring Boot 3.5.

## Avvio

Sono modalità alternative (ne basta una):

```bash
mvn spring-boot:run          # oppure: mvn clean package && java -jar target/ecommerce-0.0.1-SNAPSHOT.jar
mvn clean verify             # build + tutti i test
docker build -t ecommerce . && docker run -p 8080:8080 ecommerce
```

- Swagger UI: <http://localhost:8080/swagger-ui.html> (chiama `/auth/login`, poi *Authorize* con il token restituito)
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Console H2: <http://localhost:8080/h2-console> — JDBC URL `jdbc:h2:mem:ecommerce`, utente `sa`, password vuota
(solo nel profilo `local`; il DB in memoria è raggiungibile solo mentre l'applicazione è in esecuzione).
Anche Swagger UI e OpenAPI JSON sono esposti solo in `local` (`springdoc.*.enabled`); negli altri profili rispondono 401/404.

H2 è in memoria: i dati vengono persi al riavvio. Lo schema è creato da Flyway (`db/migration`), Hibernate si limita a validarlo.

### Profili di configurazione

- `application.properties`: impostazioni comuni (datasource, JPA, Flyway, paginazione, Swagger). Il seed è disattivato e il
  secret JWT è obbligatorio (`JWT_SECRET`, `JWT_EXPIRATION`, nessun default).
- `application-local.properties`: impostazioni di sviluppo — console H2, Swagger, seed dei dati demo (`app.seed.enabled=true`) e un
  secret JWT solo per sviluppo. Il profilo `local` è attivo di default (`spring.profiles.default=local`).
- Qualsiasi altro profilo (es. produzione): `SPRING_PROFILES_ACTIVE=prod JWT_SECRET=<min 32 caratteri> JWT_EXPIRATION=15m java -jar app.jar` — niente console,
  niente Swagger/OpenAPI, niente dati demo, niente secret di default. I test usano `src/test/resources/application-test.properties`.

## Credenziali demo

| Utente | Password   | Ruolo    | Customer collegato |
|--------|------------|----------|--------------------|
| admin  | admin123   | ADMIN    | -                  |
| mario  | mario123   | CUSTOMER | Mario Rossi (id 1, proprietario dell'ordine 1) |
| lucia  | lucia123   | CUSTOMER | Lucia Bianchi (id 2) |

Le password sono salvate come hash BCrypt. L'autenticazione è stateless: esegui il login con `POST /api/v1/auth/login` e invia il
JWT restituito nell'header `Authorization: Bearer <token>` (HTTP Basic non è accettato dalle API).

## Endpoint (`/api/v1`)

| Metodo | Path | Accesso | Note |
|--------|------|---------|------|
| POST | `/auth/register` | pubblico | crea un Customer + utente CUSTOMER (il ruolo non è mai letto dal payload); 201 + Location; username/codice fiscale/email duplicati -> 409 |
| POST | `/auth/login` | pubblico | `{username, password}` -> `{accessToken, tokenType: "Bearer", expiresIn}`; credenziali errate -> 401 |
| POST | `/customers` | ADMIN | 201 + Location; codice fiscale/email duplicati -> 409 |
| GET | `/customers` | ADMIN | paginato |
| GET | `/customers/{id}` | ADMIN o il customer stesso | |
| POST | `/products` | ADMIN | 201 + Location; codice duplicato -> 409 |
| PATCH | `/products/{id}/stock` | ADMIN | body `{"delta": n}`: positivo incrementa, negativo decrementa (lock pessimistico); risultato < 0 -> 409, delta 0 o fuori da ±1.000.000 -> 400 |
| GET | `/products`, `/products/{id}` | autenticato | paginato |
| POST | `/orders` | autenticato | l'ADMIN deve passare `customerId`; il CUSTOMER ordina solo per sé. Limiti: da 1 a 100 righe, quantità da 1 a 10000 per riga (altrimenti 400) |
| GET | `/orders` | autenticato | ADMIN: tutti gli ordini, CUSTOMER: i propri |
| GET | `/orders/{id}` | ADMIN o proprietario | |
| POST | `/orders/{id}/deliver` | ADMIN | ORDINATO -> CONSEGNATO, altrimenti 409 |
| POST | `/orders/{id}/cancel` | ADMIN o proprietario | 200, annulla l'ordine (stato ANNULLATO, l'ordine resta consultabile) e ripristina lo stock; ordine consegnato o già annullato -> 409. È una transizione di stato, quindi POST su una action e non DELETE |

La paginazione usa `page`, `size` (max 100) e `sort` (es. `sort=id,desc`). Le risposte hanno la forma
`{"content": [...], "page": 0, "size": 20, "totalElements": 3, "totalPages": 1, "first": true, "last": true}`.
Il `sort` accetta solo colonne scalari in whitelist (ordini: `id`, `stato`, `dataCreazione`; prodotti: `id`, `codice`, `nome`, `stock`; clienti: `id`, `nome`, `cognome`, `dataNascita`, `codiceFiscale`, `email`); altri campi (es. `righe.quantita`) -> 400. L'`id` è aggiunto come ultimo criterio per rendere stabile la paginazione.
I `codice` dei prodotti (lettere, cifre, `.`, `_`, `-`) sono normalizzati in maiuscolo e gli username in minuscolo: `lap-001`/`LAP-001` e `mario`/`MARIO` sono lo stesso valore.
`POST /auth/register` risponde 409 con un messaggio generico, senza rivelare quale tra username, codice fiscale o email esista già.

Gli errori sono RFC 7807 `application/problem+json`: 400 (validazione, con una mappa `errors` per campo), 401, 403, 404, 409 e 503 con `Retry-After` quando un lock non è ottenibile in tempo.
Un customer che accede all'ordine di un altro riceve **403**, e lo stesso vale per un ordine inesistente: la risposta non rivela se un id esiste (anti-enumerazione). L'ADMIN, che può vedere tutto, riceve invece **404** per un ordine inesistente.

## Esempi curl

```bash
# registra un nuovo customer, poi esegui il login e conserva il token
curl -X POST localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"carla","password":"secret123","nome":"Carla","cognome":"Gialli","dataNascita":"1995-03-10","codiceFiscale":"GLLCRL95C50H501Z","email":"carla@example.com"}'
login() { curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'; }
MARIO=$(login mario mario123)
ADMIN=$(login admin admin123)

# lista prodotti
curl -H "Authorization: Bearer $MARIO" "localhost:8080/api/v1/products?size=5&sort=nome,asc"

# il customer crea un ordine (customerId ricavato dal token)
curl -H "Authorization: Bearer $MARIO" -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":1,"quantita":2},{"productId":2,"quantita":1}]}'

# l'admin crea un ordine per un customer
curl -H "Authorization: Bearer $ADMIN" -X POST localhost:8080/api/v1/orders -H 'Content-Type: application/json' \
  -d '{"customerId":2,"items":[{"productId":3,"quantita":1}]}'

# stock insufficiente -> 409, nessuno stock viene modificato
curl -H "Authorization: Bearer $MARIO" -X POST localhost:8080/api/v1/orders -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":1,"quantita":9999}]}'

# consegna, poi annullamento (la seconda chiamata dà 409 perché l'ordine è consegnato)
curl -H "Authorization: Bearer $ADMIN" -X POST localhost:8080/api/v1/orders/1/deliver
curl -H "Authorization: Bearer $ADMIN" -X POST localhost:8080/api/v1/orders/1/cancel

# crea un customer / un prodotto (ADMIN)
curl -H "Authorization: Bearer $ADMIN" -X POST localhost:8080/api/v1/customers -H 'Content-Type: application/json' \
  -d '{"nome":"Carlo","cognome":"Gialli","dataNascita":"1980-02-03","codiceFiscale":"GLLCRL80B03H501K","email":"carlo@example.com"}'
curl -H "Authorization: Bearer $ADMIN" -X POST localhost:8080/api/v1/products -H 'Content-Type: application/json' \
  -d '{"codice":"MON-001","nome":"Monitor 27\"","stock":15}'

# modifica lo stock: +10, poi -3 (risultato negativo -> 409)
curl -H "Authorization: Bearer $ADMIN" -X PATCH localhost:8080/api/v1/products/1/stock \
  -H 'Content-Type: application/json' -d '{"delta":10}'
curl -H "Authorization: Bearer $ADMIN" -X PATCH localhost:8080/api/v1/products/1/stock \
  -H 'Content-Type: application/json' -d '{"delta":-3}'
```

## Architettura

Package by layer sotto `it.example.ecommerce`:

| Package | Contenuto |
|---------|-----------|
| `controller` | controller REST, regole `@PreAuthorize`, dialogano solo con le interfacce dei service |
| `service`, `service.impl` | interfacce dei service e implementazioni transazionali |
| `repository` | repository Spring Data JPA (query con locking) |
| `domain` | entità JPA, macchina a stati `OrderStatus`, `OrderFactory` |
| `dto` | record di request/response con Bean Validation; le entità non vengono mai esposte |
| `mapper` | mapper MapStruct |
| `exception` | eccezioni di dominio e `@RestControllerAdvice` che produce `ProblemDetail` |
| `security` | `UserDetailsService`, controlli di ownership (`orderSecurity`, `customerSecurity`), handler 401/403 |
| `config` | filter chain di sicurezza, OpenAPI, `Clock`, seeder dei dati demo |

### Pattern

- **Repository**: i repository Spring Data isolano la persistenza.
- **DTO + Mapper**: record + MapStruct mantengono l'API disaccoppiata dalle entità.
- **State**: `OrderStatus` è un enum (ORDINATO, CONSEGNATO, ANNULLATO) le cui costanti implementano `deliver()` e `cancel()` e
  restituiscono lo stato successivo; le transizioni non valide
  lanciano `InvalidOrderStateException` (409). Aggiungere uno stato non richiede modifiche al service.
- **Factory**: `OrderFactory` (classe di dominio senza annotazioni Spring, registrata come bean in `AppConfig`) costruisce un aggregato `Order`
  coerente (righe, stato, data di creazione da un `Clock` iniettato). Non tocca altri aggregati: lo stock è riservato dal service.
- **Facade**: `OrderServiceImpl` orchestra repository di customer/prodotti, factory e mapper dietro un unico punto
  di ingresso transazionale usato dal controller. La regola «chi può ordinare per chi» è nel service, tramite `Requester`
  (ruolo + customerId) applicato a creazione, lettura, consegna e annullamento (`@PreAuthorize` nel controller resta come difesa in profondità), così ogni punto di ingresso (controller, seeder, job) è soggetto allo stesso controllo.
- **Builder**: `@Builder` di Lombok sulle entità.
- Dependency injection via costruttore ovunque; i service sono esposti tramite interfacce.

### Sicurezza

JWT bearer stateless (nessuna sessione, CSRF disabilitato). `POST /auth/login` verifica username/password (BCrypt, tramite
`AuthenticationManager`) ed emette un token firmato HS256 (subject = username). Il resource server di Spring
valida firma e scadenza; `JwtUserConverter` ricarica poi l'utente dal DB, quindi ruolo e collegamento al customer sono sempre
aggiornati e i token di utenti cancellati vengono rifiutati. Si configura con `app.security.jwt.secret` (env `JWT_SECRET`, min 32 caratteri) e `app.security.jwt.expiration` (env `JWT_EXPIRATION`, es. `15m`);
i default esistono solo nel profilo `local` (15 minuti). Limiti: nessun refresh token, nessuna revoca anticipata,
nessun rate limiting sul login. Gli utenti sono nella tabella `app_users` con hash BCrypt; un utente CUSTOMER è
collegato a un `Customer`. L'autorizzazione usa la method security: `hasRole('ADMIN') or @orderSecurity.isOwner(#id, authentication)`.
Swagger UI e documentazione OpenAPI (solo se abilitati, cioè in `local`), `/auth/login` e `/auth/register` sono pubblici.

## Strategia di concorrenza

La creazione e l'annullamento degli ordini usano un **decremento/incremento atomico condizionale** a livello SQL, che prende un lock di riga:

```sql
UPDATE products SET stock = stock - :q, version = version + 1 WHERE id = :id AND stock >= :q
```

1. Le righe richieste vengono unite per id prodotto e ordinate in modo crescente.
2. Per ogni prodotto, in quell'ordine, l'`UPDATE` verifica la disponibilità e scrive in un'unica istruzione, senza finestra
   «leggi, controlla, scrivi». Se restituisce 0 righe lo stock è insufficiente (409) o il prodotto non esiste (404).
3. Tutti i lock di riga sono acquisiti nello stesso ordine di id, quindi **non si va in deadlock** (es. ordini con A,B e B,A),
   e restano fino al commit: qualsiasi errore annulla tutto, senza modifiche parziali.

Perché non il locking ottimistico + retry? Lo stock è una riga molto contesa: con il locking ottimistico, acquirenti concorrenti
degli ultimi pezzi fallirebbero e ritenterebbero ripetutamente, sprecando lavoro e richiedendo una politica di retry/backoff.
Il lock di riga serializza solo gli ordini che toccano gli stessi prodotti, dà un esito deterministico (riesce esattamente la
quantità disponibile) e non richiede logica di retry. Rispetto a `SELECT ... FOR UPDATE` + controllo in Java, l'`UPDATE` condizionale
usa un solo round trip per prodotto e tiene il lock per meno tempo, il che conta su un prodotto molto richiesto.

**Lock timeout:** un lock non ottenuto entro 2 secondi (`LOCK_TIMEOUT` nell'URL H2; su altri DB l'equivalente, es. `lock_timeout` di
PostgreSQL) libera subito thread e connessione del pool. Essendo una condizione transitoria, la risposta è **503 Service Unavailable
con `Retry-After`** (non 409). Il 409 resta per i veri conflitti di stato (stock insufficiente, transizioni non valide, `@Version`).

`Product` ha comunque una colonna `@Version`, incrementata anche dagli update atomici: un aggiornamento in conflitto da un altro percorso
di codice (es. `PATCH /products/{id}/stock`, che usa `PESSIMISTIC_WRITE`) viene segnalato come 409 invece di sovrascrivere silenziosamente lo stock.
Consegna e annullamento bloccano prima la riga dell'ordine (poi i prodotti in ordine di id), quindi consegna e
annullamento sullo stesso ordine non possono andare in race. Il database impone inoltre `stock >= 0` e `quantita > 0` con vincoli CHECK.

## Logging

Logging applicativo con SLF4J (`@Slf4j`): eventi di business (creazione/consegna/annullamento ordine, creazione prodotto, modifica stock, registrazione e login), conflitti 409 (INFO, solo il tipo di eccezione: il messaggio può contenere email, codice fiscale o username), accessi negati e lock timeout (WARN), errori inattesi (ERROR con stack trace). Password, token e dati personali (email, codice fiscale) non vengono mai loggati; i valori forniti dal client (username) passano da `LogSanitizer`, che sostituisce i caratteri di controllo (a capo inclusi) e tronca, per impedire il log forging. Livello `INFO` di default, `DEBUG` col profilo `local`; override con `LOGGING_LEVEL_IT_EXAMPLE_ECOMMERCE`.

## Test

`mvn test` esegue tutto (surefire raccoglie anche gli `*IT`):

- Unit test (Mockito): `OrderServiceImplTest`, `CatalogServicesTest`, `OrderDomainTest`, `ProductStockAdjustmentTest`: stock insufficiente senza
  modifiche parziali, transizioni di stato, cancellazione e ripristino dello stock, duplicati.
- Test di integrazione MockMvc su H2: `OrderControllerIT`, `CustomerAndProductControllerIT`, `ProductStockIT`, `AuthControllerIT`, `SecurityIT`: codici di stato,
  validazione, metadati di paginazione, admin vs customer, accesso all'ordine di un altro, rollback su 409.
- `OrderConcurrencyIT`: 20 thread in competizione per 5 unità / l'ultima unità / ordini multi-prodotto con ordine opposto; riesce esattamente
  la quantità disponibile, lo stock non va mai in negativo, nessun deadlock.
