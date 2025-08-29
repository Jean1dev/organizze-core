# Exemplo de Uso - Transações Parceladas

## 1. Criar uma transação parcelada

```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Compra de iPhone",
    "notes": "iPhone 15 Pro Max parcelado",
    "category_id": 1,
    "amount_cents": 120000,
    "installments_attributes": {
      "periodicity": "monthly",
      "total": 12
    }
  }'
```

**Resposta esperada:**
```json
{
  "id": 1
}
```

## 2. Listar todas as transações

```bash
curl -X GET http://localhost:8080/transactions
```

**Resposta esperada:**
```json
[
  {
    "id": 1,
    "description": "Compra de iPhone",
    "date": "2024-01-15",
    "category_id": 1,
    "amount_cents": 120000,
    "is_installment": true,
    "installment_periodicity": "monthly",
    "installment_total": 12
  }
]
```

## 3. Listar parcelas de uma transação

```bash
curl -X GET http://localhost:8080/transactions/1/installments
```

**Resposta esperada:**
```json
[
  {
    "id": 1,
    "installment_number": 1,
    "due_date": "2024-01-15",
    "amount_cents": 10000,
    "paid": false,
    "created_at": "2024-01-15T10:00:00Z"
  },
  {
    "id": 2,
    "installment_number": 2,
    "due_date": "2024-02-15",
    "amount_cents": 10000,
    "paid": false,
    "created_at": "2024-01-15T10:00:00Z"
  }
]
```

## 4. Criar transação sem parcelas (comportamento normal)

```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Almoço",
    "notes": "Almoço no restaurante",
    "category_id": 1,
    "amount_cents": 5000
  }'
```

## 5. Exemplo com parcelas semanais

```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Curso online",
    "notes": "Curso de programação",
    "category_id": 1,
    "amount_cents": 40000,
    "installments_attributes": {
      "periodicity": "weekly",
      "total": 8
    }
  }'
```

## 6. Exemplo com parcelas anuais

```bash
curl -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Seguro do carro",
    "notes": "Seguro anual",
    "category_id": 1,
    "amount_cents": 240000,
    "installments_attributes": {
      "periodicity": "yearly",
      "total": 3
    }
  }'
```

## Notas Importantes

1. **Valor das parcelas**: O valor total é dividido igualmente entre todas as parcelas
2. **Datas de vencimento**: A primeira parcela vence na data de criação da transação
3. **Campos obrigatórios**: `description`, `category_id`, `amount_cents`
4. **Campos opcionais**: `notes`, `tags`, `installments_attributes`
5. **Periodicidades suportadas**: `monthly`, `weekly`, `yearly`
