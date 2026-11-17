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

Example: <http://localhost:8080/api/products?category=coffee-gear&q=brew&sort=name&direction=desc>

The frontend is served by `GET /`. Thymeleaf only resolves the page and asset paths; it does not render product rows.

## Bottom command bar

The command bar stays fixed at the bottom of the page. It uses simple client-side phrase matching to set the existing filters; it does not call an AI service. Examples:

- `show wireless headphones`
- `show coffee gear sorted Z to A`
- `sort by category`
- `next page` or `previous page`
- `show all products` to clear the filters
