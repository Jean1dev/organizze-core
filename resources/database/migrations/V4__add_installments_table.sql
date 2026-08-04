CREATE TABLE installments (
    id SERIAL PRIMARY KEY,
    transaction_id INT NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    amount_cents INT NOT NULL,
    paid BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE,
    CONSTRAINT unique_installment UNIQUE (transaction_id, installment_number)
);

ALTER TABLE transactions 
ADD COLUMN is_installment BOOLEAN DEFAULT FALSE,
ADD COLUMN installment_periodicity VARCHAR(20),
ADD COLUMN installment_total INT;
