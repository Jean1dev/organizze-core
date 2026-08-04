CREATE TABLE monthly_interest (
    id SERIAL PRIMARY KEY,
    amount_cents INT NOT NULL,
    year INT NOT NULL,
    month INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
