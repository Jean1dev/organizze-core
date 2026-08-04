CREATE TABLE transactions (
    id SERIAL PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    notes TEXT,
    date DATE NOT NULL,
    category_id INT NOT NULL,
    amount_cents INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE transaction_tags (
    id SERIAL PRIMARY KEY,
    transaction_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
);
