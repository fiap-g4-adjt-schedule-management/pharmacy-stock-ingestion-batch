# Pharmacy Stock Ingestion Batch

![Java](https://img.shields.io/badge/Java-21-red?logo=openjdk&logoColor=white)
![Azure Functions](https://img.shields.io/badge/Azure%20Functions-Serverless-blue?logo=azurefunctions&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-336791?logo=postgresql&logoColor=white)
![Azure Blob Storage](https://img.shields.io/badge/Azure%20Blob%20Storage-Storage-0078D4?logo=microsoftazure&logoColor=white)

## 📌 Arquitetura

Este projeto segue o estilo **Arquitetura Hexagonal (Ports & Adapters)**, visando separar regras de negócio de detalhes de infraestrutura.

- **Domínio / Regras**: validações e regras de cálculo de `stock_status`.
- **Aplicação (Use Cases)**: orquestra o fluxo de ingestão (listar blobs → validar → persistir → mover arquivo).
- **Adapters de Entrada (Inbound)**: **Azure Function Timer Trigger** (gatilho por CRON) que dispara o caso de uso.
- **Adapters de Saída (Outbound)**: integrações com **Azure Blob Storage** (leitura/movimentação do arquivo) e **PostgreSQL** (persistência / idempotência).


## ⚙️ Tecnologias

- **Java 21**
- **Azure Functions** (Timer Trigger)
- **Azure Blob Storage**
- **PostgreSQL**
- **Maven**

## 🎯 Objetivo

O projeto é um batch **Timer Trigger** em **Java 21** (Azure Functions) para ingerir arquivos CSV de estoque de farmácias do **Azure Blob Storage** e persistir/atualizar os dados em um **PostgreSQL**.

A cada execução (via `CRON_TIME`):

1. Lista blobs dentro do prefixo `INBOX_PREFIX` (ex.: `inbox/`) no container configurado.
2. Filtra apenas arquivos “antigos o suficiente” (por padrão `MIN_BLOB_AGE_MINUTES`, para evitar pegar upload ainda em andamento).
3. Para cada arquivo elegível:
   - Extrai o **CNPJ** a partir do caminho do blob: `inbox/{CNPJ}/{arquivo}.csv`
   - Extrai a **data de referência** a partir do nome do arquivo (ver padrão abaixo)
   - Garante **idempotência** via tabela `file_ingestion_control` (chave `(blob_path, etag)`)
   - Valida o arquivo (extensão, cabeçalho e linhas)
   - Verifica se o CNPJ existe na tabela `pharmacy`
   - Resolve `medicine_code` a partir do `medicine_name` consultando a tabela `medication_name`
   - Calcula o `stock_status` com base na quantidade:
     - `< 10`  → `CRITICAL`
     - `10..30` → `NORMAL`
     - `> 30` → `HIGH`
   - Faz **upsert** na tabela `pharmacy_medicine_stock` (validação por `(pharmacy_id, medicine_code)`)
4. Ao final, move o blob para:
   - `PROCESSED_PREFIX` (ex.: `processed/`) quando processado com sucesso
   - `ERROR_PREFIX` (ex.: `error/`) quando falhar

O log ao final imprime um resumo: `eligible`, `processed`, `failed`, `duplicates`.


## 📝 Regras

- A idempotência é feita por `(blob_path, etag)` na tabela `file_ingestion_control`.
- Se um arquivo já estiver como `PROCESSED`, o batch tenta reconciliação movendo o blob para `processed/`.
- Se estiver `FAILED`, tenta mover para `error/`.
- Se ao mover o blob o destino já existir, o batch trata como duplicado e não sobrescreve.


---

## 🗂️ Arquivos

### Caminho do blob

O código espera o padrão:

```
inbox/{CNPJ}/{nome-do-arquivo}.csv
```

### Nome do arquivo

O batch extrai a **data de referência** a partir do **3º segmento** do nome do arquivo (separado por `_`).

Padrão mínimo aceito:

```
<qualquer>_<CNPJ>_<YYYY-MM-DD>_<TIMESTAMP>.csv
```

Exemplo:

```
stock_02964944000104_2026-02-12_20260211T093000Z.csv
```

### Formato do CSV

- **Delimitador:** `;` (ponto e vírgula)
- **Cabeçalho obrigatório:**

```
cnpj;medicine_name;quantity;reference_date
```

- Regras importantes:
  - `cnpj` deve ter **14 dígitos** (apenas números)
  - `reference_date` deve estar em `yyyy-MM-dd` e **bater** com a data extraída do nome do arquivo
  - `quantity` deve ser inteiro e `>= 0`
  - `medicine_name` é obrigatório

Exemplo de conteúdo:

```
cnpj;medicine_name;quantity;reference_date
12345678000199;LOSARTANA POTASSICA 50MG;12;2026-02-13
12345678000199;ETINILESTRADIOL 0.03MG;5;2026-02-13
```

---

## ▶️ Como rodar localmente

### Pré-requisitos

- **Java 21**
- **Maven**
- **Azure Functions Core Tools** (para executar localmente)
- Acesso ao **PostgreSQL**
- Acesso ao **Azure Storage Account** (Blob) ou uso de emulador

### 1) Criar o `local.settings.json` (obrigatório)

Por segurança, **esse arquivo não é versionado no Git**, porque contém segredos (connection strings, senha de banco etc.).

Crie um arquivo `local.settings.json` na raiz do projeto com **os mesmos nomes** de variáveis abaixo:

```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "<STORAGE_CONNECTION_STRING>",
    "FUNCTIONS_WORKER_RUNTIME": "java",

    "CRON_TIME": "0 0 */3 * * *",

    "BLOB_CONNECTION": "<BLOB_CONNECTION_STRING>",
    "BLOB_CONTAINER": "<CONTAINER_NAME>",
    "INBOX_PREFIX": "inbox/",
    "PROCESSED_PREFIX": "processed/",
    "ERROR_PREFIX": "error/",
    "MIN_BLOB_AGE_MINUTES": "15",

    "DB_URL": "<HOST>:<PORT>/<DATABASE>",
    "DB_USER": "<USERNAME>",
    "DB_PASSWORD": "<PASSWORD>"
  }
}
```

### 2) Subir o banco (mínimo necessário)

O batch espera as tabelas abaixo (ajuste os tipos conforme seu schema real):

```sql
-- Farmácias (verificação de existência por CNPJ)
CREATE TABLE IF NOT EXISTS pharmacy (
  cnpj VARCHAR(14) PRIMARY KEY
);

-- Mapeamento de nome do medicamento para código
CREATE TABLE IF NOT EXISTS medication_name (
  medicine_name TEXT PRIMARY KEY,
  medicine_code TEXT NOT NULL
);

-- Controle de ingestão / idempotência
CREATE TABLE IF NOT EXISTS file_ingestion_control (
  id BIGSERIAL PRIMARY KEY,
  blob_path TEXT NOT NULL,
  etag TEXT NOT NULL,
  file_name TEXT NOT NULL,
  cnpj VARCHAR(14) NOT NULL,
  reference_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ,
  error_reason TEXT,
  UNIQUE (blob_path, etag)
);

-- Estoque por farmácia e medicamento
CREATE TABLE IF NOT EXISTS pharmacy_medicine_stock (
  pharmacy_id VARCHAR(14) NOT NULL,
  medicine_code TEXT NOT NULL,
  quantity INT NOT NULL,
  stock_status VARCHAR(20) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (pharmacy_id, medicine_code)
);
```

### 3) Executar o projeto

Na raiz do projeto:

```bash
mvn clean package
mvn azure-functions:run
```
# Pharmacy Stock Ingestion Batch

![Java](https://img.shields.io/badge/Java-21-red?logo=openjdk&logoColor=white)
![Azure Functions](https://img.shields.io/badge/Azure%20Functions-Serverless-blue?logo=azurefunctions&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-336791?logo=postgresql&logoColor=white)
![Azure Blob Storage](https://img.shields.io/badge/Azure%20Blob%20Storage-Storage-0078D4?logo=microsoftazure&logoColor=white)

## 📌 Arquitetura

Este projeto segue o estilo **Arquitetura Hexagonal (Ports & Adapters)**, visando separar regras de negócio de detalhes de infraestrutura.

- **Domínio / Regras**: validações e regras de cálculo de `stock_status`.
- **Aplicação (Use Cases)**: orquestra o fluxo de ingestão (listar blobs → validar → persistir → mover arquivo).
- **Adapters de Entrada (Inbound)**: **Azure Function Timer Trigger** (gatilho por CRON) que dispara o caso de uso.
- **Adapters de Saída (Outbound)**: integrações com **Azure Blob Storage** (leitura/movimentação do arquivo) e **PostgreSQL** (persistência / idempotência).


## ⚙️ Tecnologias

- **Java 21**
- **Azure Functions** (Timer Trigger)
- **Azure Blob Storage**
- **PostgreSQL**
- **Maven**

## 🎯 Objetivo

O projeto é um batch **Timer Trigger** em **Java 21** (Azure Functions) para ingerir arquivos CSV de estoque de farmácias do **Azure Blob Storage** e persistir/atualizar os dados em um **PostgreSQL**.

A cada execução (via `CRON_TIME`):

1. Lista blobs dentro do prefixo `INBOX_PREFIX` (ex.: `inbox/`) no container configurado.
2. Filtra apenas arquivos “antigos o suficiente” (por padrão `MIN_BLOB_AGE_MINUTES`, para evitar pegar upload ainda em andamento).
3. Para cada arquivo elegível:
   - Extrai o **CNPJ** a partir do caminho do blob: `inbox/{CNPJ}/{arquivo}.csv`
   - Extrai a **data de referência** a partir do nome do arquivo (ver padrão abaixo)
   - Garante **idempotência** via tabela `file_ingestion_control` (chave `(blob_path, etag)`)
   - Valida o arquivo (extensão, cabeçalho e linhas)
   - Verifica se o CNPJ existe na tabela `pharmacy`
   - Resolve `medicine_code` a partir do `medicine_name` consultando a tabela `medication_name`
   - Calcula o `stock_status` com base na quantidade:
     - `< 10`  → `CRITICAL`
     - `10..30` → `NORMAL`
     - `> 30` → `HIGH`
   - Faz **upsert** na tabela `pharmacy_medicine_stock` (validação por `(pharmacy_id, medicine_code)`)
4. Ao final, move o blob para:
   - `PROCESSED_PREFIX` (ex.: `processed/`) quando processado com sucesso
   - `ERROR_PREFIX` (ex.: `error/`) quando falhar

O log ao final imprime um resumo: `eligible`, `processed`, `failed`, `duplicates`.


## 📝 Regras

- A idempotência é feita por `(blob_path, etag)` na tabela `file_ingestion_control`.
- Se um arquivo já estiver como `PROCESSED`, o batch tenta reconciliação movendo o blob para `processed/`.
- Se estiver `FAILED`, tenta mover para `error/`.
- Se ao mover o blob o destino já existir, o batch trata como duplicado e não sobrescreve.


---

## 🗂️ Arquivos

### Caminho do blob

O código espera o padrão:

```
inbox/{CNPJ}/{nome-do-arquivo}.csv
```

### Nome do arquivo

O batch extrai a **data de referência** a partir do **3º segmento** do nome do arquivo (separado por `_`).

Padrão mínimo aceito:

```
<qualquer>_<CNPJ>_<YYYY-MM-DD>_<TIMESTAMP>.csv
```

Exemplo:

```
stock_02964944000104_2026-02-12_20260211T093000Z.csv
```

### Formato do CSV

- **Delimitador:** `;` (ponto e vírgula)
- **Cabeçalho obrigatório:**

```
cnpj;medicine_name;quantity;reference_date
```

- Regras importantes:
  - `cnpj` deve ter **14 dígitos** (apenas números)
  - `reference_date` deve estar em `yyyy-MM-dd` e **bater** com a data extraída do nome do arquivo
  - `quantity` deve ser inteiro e `>= 0`
  - `medicine_name` é obrigatório

Exemplo de conteúdo:

```
cnpj;medicine_name;quantity;reference_date
12345678000199;LOSARTANA POTASSICA 50MG;12;2026-02-13
12345678000199;ETINILESTRADIOL 0.03MG;5;2026-02-13
```

---

## ▶️ Como rodar localmente

### Pré-requisitos

- **Java 21**
- **Maven**
- **Azure Functions Core Tools** (para executar localmente)
- Acesso ao **PostgreSQL**
- Acesso ao **Azure Storage Account** (Blob) ou uso de emulador

### 1) Criar o `local.settings.json` (obrigatório)

Por segurança, **esse arquivo não é versionado no Git**, porque contém segredos (connection strings, senha de banco etc.).

Crie um arquivo `local.settings.json` na raiz do projeto com **os mesmos nomes** de variáveis abaixo:

```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "<STORAGE_CONNECTION_STRING>",
    "FUNCTIONS_WORKER_RUNTIME": "java",

    "CRON_TIME": "0 0 */3 * * *",

    "BLOB_CONNECTION": "<BLOB_CONNECTION_STRING>",
    "BLOB_CONTAINER": "<CONTAINER_NAME>",
    "INBOX_PREFIX": "inbox/",
    "PROCESSED_PREFIX": "processed/",
    "ERROR_PREFIX": "error/",
    "MIN_BLOB_AGE_MINUTES": "15",

    "DB_URL": "<HOST>:<PORT>/<DATABASE>",
    "DB_USER": "<USERNAME>",
    "DB_PASSWORD": "<PASSWORD>"
  }
}
```

### 2) Subir o banco (mínimo necessário)

O batch espera as tabelas abaixo (ajuste os tipos conforme seu schema real):

```sql
-- Farmácias (verificação de existência por CNPJ)
CREATE TABLE IF NOT EXISTS pharmacy (
  cnpj VARCHAR(14) PRIMARY KEY
);

-- Mapeamento de nome do medicamento para código
CREATE TABLE IF NOT EXISTS medication_name (
  medicine_name TEXT PRIMARY KEY,
  medicine_code TEXT NOT NULL
);

-- Controle de ingestão / idempotência
CREATE TABLE IF NOT EXISTS file_ingestion_control (
  id BIGSERIAL PRIMARY KEY,
  blob_path TEXT NOT NULL,
  etag TEXT NOT NULL,
  file_name TEXT NOT NULL,
  cnpj VARCHAR(14) NOT NULL,
  reference_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ,
  error_reason TEXT,
  UNIQUE (blob_path, etag)
);

-- Estoque por farmácia e medicamento
CREATE TABLE IF NOT EXISTS pharmacy_medicine_stock (
  pharmacy_id VARCHAR(14) NOT NULL,
  medicine_code TEXT NOT NULL,
  quantity INT NOT NULL,
  stock_status VARCHAR(20) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (pharmacy_id, medicine_code)
);
```

### 3) Executar o projeto

Na raiz do projeto:

```bash
mvn clean package
mvn azure-functions:run
```