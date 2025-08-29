# Transações Parceladas

A API agora suporta transações parceladas através do campo `installments_attributes` no payload.

## Estrutura do Payload

```json
{
  "description": "Compra de eletrodoméstico",
  "notes": "Compra parcelada em 12x",
  "category_id": 1,
  "amount_cents": 120000,
  "installments_attributes": {
    "periodicity": "monthly",
    "total": 12
  }
}
```

## Campos do installments_attributes

- `periodicity`: Frequência das parcelas
  - `"monthly"`: Parcelas mensais
  - `"weekly"`: Parcelas semanais  
  - `"yearly"`: Parcelas anuais
- `total`: Número total de parcelas

## Endpoints

### POST /transactions
Cria uma nova transação (com ou sem parcelas)

### GET /transactions
Lista todas as transações com informações sobre parcelas

### GET /transactions/:id/installments
Lista todas as parcelas de uma transação específica

## Exemplo de Resposta - GET /transactions

```json
[
  {
    "id": 1,
    "description": "Compra de eletrodoméstico",
    "date": "2024-01-15",
    "category_id": 1,
    "amount_cents": 120000,
    "is_installment": true,
    "installment_periodicity": "monthly",
    "installment_total": 12
  }
]
```

## Exemplo de Resposta - GET /transactions/:id/installments

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

## Cálculo de Parcelas

O valor de cada parcela é calculado dividindo o valor total pelo número de parcelas:
- Valor total: 120000 centavos (R$ 1.200,00)
- Número de parcelas: 12
- Valor por parcela: 10000 centavos (R$ 100,00)

## Datas de Vencimento

As datas de vencimento são calculadas automaticamente baseadas na periodicidade:
- **monthly**: Adiciona 1 mês para cada parcela
- **weekly**: Adiciona 1 semana para cada parcela
- **yearly**: Adiciona 1 ano para cada parcela

A primeira parcela vence na data de criação da transação.
