# Product catalog

A small Spring Boot catalog with a PostgreSQL backend, a Thymeleaf page shell, and jQuery AJAX for product data. On first startup it inserts **300 distinct sample products in 50 categories**. There are no write APIs.

## Run on Windows (PowerShell)

Requires Java 17+, Maven, and a running PostgreSQL server. From the project folder, create the database once. Enter your PostgreSQL `postgres` user's password when prompted:

```powershell
cd 'C:\Users\HP\Desktop\Projects\agentic web'
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h localhost -U postgres -d postgres -W -c 'CREATE DATABASE product_catalog;'
```

If you already created `product_catalog`, skip the database creation command. Set the password to your **actual PostgreSQL password** and start the app:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/product_catalog'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = 'YOUR_POSTGRES_PASSWORD'
mvn spring-boot:run
```

Open <http://localhost:8080/>. Stop the server with `Ctrl+C`. The server creates tables and loads 300 sample products on the first start; later starts do not duplicate them.

If `psql.exe` is installed elsewhere, replace its path in the first command. On other systems, run the equivalent `psql -h localhost -U postgres -d postgres -c 'CREATE DATABASE product_catalog;'` command.

## Read API

- `GET /api/categories` — all 50 categories, sorted by name; each has `slug` and `name`.
- `GET /api/products` — paginated products with `id`, `name`, `description`, `category`, and `keywords`. Query parameters:
  - `category`: category slug, e.g. `coffee-gear`.
  - `q`: case-insensitive match in product name, description, or a keyword.
  - `sort`: `name` (default) or `category`.
  - `direction`: `asc` (default) or `desc`.
  - `page`: zero-based page number (default `0`).
  - `size`: page size from 1 to 100 (default `12`).

- `POST /api/ai/prompt` — sends a JSON prompt through Spring AI to the locally running Ollama `llama3.1:8b` model. The backend first selects one supported intent, then makes an intent-specific second call to extract parameters or answer a general question. Request: `{"prompt":"Show coffee gear"}`. Catalog response: `{"intent":"filter_by_category","parameters":{"categorySlug":"coffee-gear"},"response":null}`. General-question response includes the answer in `response`. Ollama must be running at `http://localhost:11434` (or set `OLLAMA_BASE_URL`).

The runtime JSON Schemas for intent selection and intent execution are maintained in `src/main/resources/intent-base.json` and passed to Ollama for structured output enforcement.

  ```powershell
  Invoke-RestMethod -Uri 'http://localhost:8080/api/ai/prompt' -Method Post -ContentType 'application/json' -Body '{"prompt":"Say hello"}'
  ```

Example: <http://localhost:8080/api/products?category=coffee-gear&q=brew&sort=name&direction=desc>

## AI agentic behavior

The command bar is a small agentic interaction loop: the user describes an outcome, the model selects the catalog capability that should handle it, and the browser performs the returned operation. The model does not directly manipulate the DOM or database.

```text
user prompt
    -> intent-selection call (all supported intents)
    -> selected intent
    -> intent-execution call (intent-specific parameters)
    -> validated JSON response
    -> frontend applyIntent(...)
    -> catalog API request and UI update
```

The backend uses two chained calls to the local Ollama `llama3.1:8b` model:

1. **Intent routing** — `AiIntentService` sends the complete list of supported intents and asks the model to select exactly one. No parameters are extracted in this step.
2. **Intent execution** — the backend builds a second prompt for the selected intent and asks the model to return only that intent's parameters. For category filtering, the current category slugs are supplied as an allow-list, so the model cannot invent a category value.
3. **Structured response** — `OllamaChatOptions.outputSchema(...)` applies the corresponding JSON Schema from `intent-base.json` to both calls. The service parses the JSON, normalizes unsupported values to `unknown`, and returns `{intent, parameters, response}`.
4. **Deterministic UI action** — `command.js` maps the returned intent to a known browser operation. It updates the existing filters and calls the normal product API; the model never constructs an arbitrary URL or executes JavaScript.

Supported intents are `list_products`, `filter_by_category`, `search_products`, `sort_products`, `next_page`, `previous_page`, `reset_filters`, `general_ai_question`, and `unknown`. Catalog intents return parameters for the UI. A `general_ai_question` returns a short answer in `response` without changing the catalog.

For example, a request such as `{"prompt":"I want something to read"}` can produce:

```json
{
  "intent": "filter_by_category",
  "parameters": {"categorySlug": "books"},
  "response": null
}
```

This proof of concept demonstrates intent-driven application control rather than a fully autonomous agent: the allowed capabilities, parameter schemas, and browser-side effects remain explicit and auditable. The model supplies interpretation; application code owns validation and execution.

Every model request and response is written to `logs/ai-intent.log` with its stage (`intent-selection` or `intent-execution:<intent>`). The log file is local runtime output and is excluded from Git commits.

The frontend is served by `GET /`. Thymeleaf only resolves the page and asset paths; it does not render product rows. The catalog starts empty until the user chooses a category, searches, changes a sort, or uses the command bar.

AI request and response logs are written to `logs/ai-intent.log` by default. Set `AI_LOG_FILE` to use a different file path. The AI service logger is separated from the console logger, so these entries are not printed to the console.

## Bottom command bar

The command bar stays fixed at the bottom of the page and sends natural-language requests to `POST /api/ai/prompt`. The backend selects one supported intent, extracts its parameters in a second structured-output call, and returns that result to the browser. The frontend applies the returned intent to the catalog; the submit button is disabled and shows a spinner while the operation is running.

The following are illustrative natural-language prompts, not a fixed command vocabulary or client-side phrase rules. Any prompt is sent to the model for intent selection:

- `show wireless headphones` — search products
- `show coffee gear` — filter by category
- `show coffee gear sorted Z to A` — filter and sort
- `sort by category` — change ordering
- `next page` or `previous page` — navigate results
- `show all products` — list products and clear filters
- `clear filters` — reset the catalog view
