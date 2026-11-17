# Product catalog

A small Spring Boot catalog with a PostgreSQL backend, a Thymeleaf page shell, and jQuery AJAX for product data. On first startup it inserts **300 distinct sample products in 50 categories**. There are no write APIs.

## Run on Windows (PowerShell)

Requires Java 17+, Maven, and a running PostgreSQL server. The AI command bar also requires Ollama with the `llama3.1:8b` model installed and running locally:

```powershell
ollama pull llama3.1:8b
ollama serve
```

Run `ollama serve` only if Ollama is not already running, and keep it running in a separate terminal. The default Ollama address is `http://localhost:11434`; set `OLLAMA_BASE_URL` before starting the application to override it. Manual catalog controls use the catalog APIs without calling Ollama.

From the project folder, create the database once. Enter your PostgreSQL `postgres` user's password when prompted:

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

## APIs

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
    -> parsed JSON result {intent, parameters, response}
    -> frontend applyIntent(...)
    -> catalog API request and UI update for catalog actions
```

The backend uses two chained calls to the local Ollama `llama3.1:8b` model:

1. **Intent routing** — `AiIntentService` sends the complete list of supported intents and asks the model to select exactly one. No parameters are extracted in this step.
2. **Parameter extraction or answer generation** — the backend builds a second prompt for the selected intent. Catalog actions return parameters; general questions return a question parameter and an answer. For category filtering, category names and slugs are read from PostgreSQL and included in the prompt, and the schema's category enum is populated with those slugs.
3. **Structured response** — `OllamaChatOptions.outputSchema(...)` supplies a JSON Schema from `intent-base.json` on each call to constrain model generation. The backend parses the result, maps unsupported intent names to `unknown`, and returns `{intent, parameters, response}`. It does not run a separate JSON Schema validator on the returned parameters. Empty or invalid JSON responses raise an error.
4. **UI execution** — `command.js` dispatches the returned intent to a predefined browser operation. It checks category existence, search input, sort values, and pagination button state before applying those actions. Filter changes trigger the normal product API. The model supplies intent and parameter data; application code performs the operation.

The supported capabilities and their UI effects are:

| Intent | Parameters | Frontend effect |
| --- | --- | --- |
| `list_products` | `{}` | Clear category/search, reset sort to name ascending, and load page 1. |
| `filter_by_category` | `categorySlug` | Apply one existing category and load page 1, preserving search and sort. |
| `search_products` | `keywordQuery` | Set search text (up to 100 characters) and load page 1, preserving category and sort. |
| `sort_products` | `sortField`, `sortDirection` | Sort by `name` or `category`, with `asc` or `desc`, and load page 1. |
| `next_page` | `pageDirection: "next"` | Trigger next-page navigation when its button is enabled. |
| `previous_page` | `pageDirection: "previous"` | Trigger previous-page navigation when its button is enabled. |
| `reset_filters` | `{}` | Clear category/search, reset sort, and load page 1. |
| `general_ai_question` | `question` | Log the returned `response` in the browser console; leave catalog controls unchanged. |
| `unknown` | `{}` | Show feedback that no catalog action was identified. |

For example, a request such as `{"prompt":"I want something to read"}` can produce:

```json
{
  "intent": "filter_by_category",
  "parameters": {"categorySlug": "books"},
  "response": null
}
```

This proof of concept explores using AI as a virtual user: someone expresses a need in their own words, the model selects an available capability and its parameters, and the application carries it out. The backend orchestrates a fixed two-call workflow, with one selected intent per submission.

### Current limits

- Each submission selects exactly one intent. A combined request such as "show coffee gear sorted Z to A" does not produce both filtering and sorting actions. Submit a category request and then a separate sorting request; the UI preserves existing filters where described above.
- The frontend sends only the current user prompt to the backend. The backend adds intent instructions and, for category filtering, available categories. There is no conversation memory, and current UI filters, page number, and total pages are not sent to the model. The `inputFormat.context` structure in `intent-base.json` describes context that is not currently wired into the endpoint.
- The runtime reads the `schemas` section of `intent-base.json`. The intent allow-list, routing prompt, and per-intent prompts are maintained in `AiIntentService.java`; adding an intent also requires backend and frontend dispatcher changes.
- JSON Schema constrains the output structure and allowed values supplied to Ollama. It does not establish that the model interpreted the user's meaning correctly. Backend parameter validation is limited as described above.
- There is no automatic retry, clarification conversation, or follow-up model call to check whether the UI action succeeded. General answers receive no product records and are printed to the browser console.

### Logging

The service writes each constructed prompt and returned response text to `logs/ai-intent.log`, labelled `intent-selection` or `intent-execution:<intent>`. The output schema, model options, and full Ollama HTTP payload are not logged. A failed call may have a request entry without a response entry.

Set `AI_LOG_FILE` to change the active log file path. The AI service logger writes these entries to the file only. The default `logs/` directory is excluded from Git commits.

The frontend is served by `GET /`. Thymeleaf only resolves the page and asset paths; it does not render product rows. The catalog starts empty until the user chooses a category, searches, changes a sort, or uses the command bar.

## Bottom command bar

The command bar stays fixed at the bottom of the page. Every nonempty submission is sent to `POST /api/ai/prompt` for model interpretation. The input and submit button are disabled and the button shows a spinner until that AI request completes or fails. Product fetching and rendering happen separately; the busy indicator does not currently wait for them to finish.

Users can describe needs naturally, for example "I want something to read" or "I want to start cycling." These are illustrative inputs for the model, not required phrases or a command vocabulary. The selected intent determines the single operation applied by the UI.
