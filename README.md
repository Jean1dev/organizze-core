# Organizze Core API

API core para o sistema Organizze desenvolvida em Clojure.

## Deploy no Heroku

### Pré-requisitos
- Conta no Heroku
- Heroku CLI instalado
- Git configurado

### Passos para deploy

1. **Faça login no Heroku:**
   ```bash
   heroku login
   ```

2. **Crie um novo app no Heroku:**
   ```bash
   heroku create seu-app-name
   ```

3. **Configure as variáveis de ambiente:**
   ```bash
   heroku config:set DATABASE_URL="jdbc:mysql://seu-host:3306/seu-banco?useSSL=false&allowPublicKeyRetrieval=true&verifyServerCertificate=false"
   heroku config:set DATABASE_USER="seu-usuario"
   heroku config:set DATABASE_PASSWORD="sua-senha"
   ```

4. **Adicione o buildpack do Clojure:**
   ```bash
   heroku buildpacks:set heroku/clojure
   ```

5. **Faça o deploy:**
   ```bash
   git add .
   git commit -m "Configuração para Heroku"
   git push heroku main
   ```

6. **Abra a aplicação:**
   ```bash
   heroku open
   ```

### Estrutura do projeto

- `src/app/core.clj` - Ponto de entrada da aplicação
- `src/app/components/` - Componentes do sistema
- `src/app/routes/` - Rotas da API
- `resources/database/migrations/` - Migrações do banco de dados
- `resources/config.edn` - Configuração da aplicação
- `deps.edn` - Dependências e aliases do projeto

### Tecnologias utilizadas

- Clojure
- Clojure CLI (deps.edn)
- Pedestal (framework web)
- Component (sistema de componentes)
- Next.jdbc (acesso ao banco)
- Flyway (migrações)
- MySQL

### Desenvolvimento local

Para executar localmente:
```bash
clojure -M:dev
```

Para executar testes:
```bash
clojure -M:test
```
