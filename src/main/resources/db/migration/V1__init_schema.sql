CREATE TABLE customers (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome           VARCHAR(100) NOT NULL,
    cognome        VARCHAR(100) NOT NULL,
    data_nascita   DATE         NOT NULL,
    codice_fiscale VARCHAR(16)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    CONSTRAINT uk_customers_codice_fiscale UNIQUE (codice_fiscale),
    CONSTRAINT uk_customers_email UNIQUE (email)
);

CREATE TABLE products (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    codice  VARCHAR(50)  NOT NULL,
    nome    VARCHAR(200) NOT NULL,
    stock   INT          NOT NULL,
    version BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_products_codice UNIQUE (codice),
    CONSTRAINT ck_products_stock CHECK (stock >= 0)
);

CREATE TABLE orders (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id    BIGINT                   NOT NULL,
    stato          VARCHAR(20)              NOT NULL,
    data_creazione TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);
CREATE INDEX idx_orders_customer ON orders (customer_id);

CREATE TABLE order_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantita   INT    NOT NULL,
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_items_quantita CHECK (quantita > 0)
);
CREATE INDEX idx_items_order ON order_items (order_id);

CREATE TABLE app_users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(100) NOT NULL,
    password    VARCHAR(100) NOT NULL,
    role        VARCHAR(20)  NOT NULL,
    customer_id BIGINT,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_customer UNIQUE (customer_id),
    CONSTRAINT fk_users_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_users_customer_role CHECK (role <> 'CUSTOMER' OR customer_id IS NOT NULL)
);
