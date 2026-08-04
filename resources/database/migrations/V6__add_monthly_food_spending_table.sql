CREATE TABLE monthly_food_spending (
    id SERIAL PRIMARY KEY,
    amount_cents INT NOT NULL,
    year INT NOT NULL,
    month INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
