# Storage Manager

Inventory application with Angular and a Java REST API for managing physical items across storage locations. Built to solve a real problem: tracking where items like drone parts, batteries, controllers, and other gear are stored across multiple drawers and compartments — so you never have to tear apart a room looking for something again.

## Local demo

Run the app with Docker at http://localhost:8080. Follow the
[five-minute interview walkthrough](docs/local-demo.md) to try consumables,
reusable items, movement history and privacy between two accounts.

The demo runs locally; no public hosting or shared demo credentials are configured.
[Mobile installation groundwork](docs/pwa-installation.md) provides a manifest and
icons; full installation still needs HTTPS and checks on actual devices.

![Inventory with synthetic demonstration data](docs/images/inventory-desktop.jpg)

[Mobile layout](docs/images/inventory-mobile.jpg) · Captures use fictitious inventory.

```mermaid
flowchart LR
    Browser["Angular in the browser"] -->|"HTTP /api + Bearer"| App["Spring Boot: web + API"]
    App -->|"JPA + transactions"| DB[("PostgreSQL volume")]
```

## Tech Stack

- **Java 21** + **Spring Boot 4.x**
- **Angular 21** + TypeScript
- **PostgreSQL 16**
- **Spring Security** with JWT authentication
- **Spring Data JPA** with Hibernate
- **Swagger/OpenAPI** for interactive documentation
- **Docker** + Docker Compose
- **Lombok** for boilerplate reduction
- **BCrypt** for password hashing

## Features

- Reversible archiving of items and locations while preserving private/shared histories
- Direct unit replenishment with a preview and private/shared restock history
- Explicit consumable/reusable types: reusable objects cannot be consumed
- House-based locations and inventory with private/shared items and own-profile access
- JWT-based authentication; public registrations receive the VIEWER role
- Item movement tracking with full history — every time an item is moved between locations, the system records where it came from, where it went, the quantity moved, and the timestamp
- Search and filtering — find items by name, category, or location
- Automatic API documentation via Swagger UI
- Centralized error handling for API responses
- Input validation for location and item requests
- Dockerized for easy deployment

## Web interface

The first Angular delivery supports registration/login, house creation/joining,
owner invitations, nested location creation, and item creation with quantity and
private/shared visibility and consumable/reusable type. Inventory supports search
and stock/visibility/type filters. See [item behavior](docs/item-kinds.md).
Desktop and mobile layouts include owner editing, consumption and consumption
history, full-quantity movement within a house and movement history. Reusable
items support taking for use and returning, with availability and usage history.
See [usage rules](docs/reusable-usage.md). Owner-only deletion with confirmation and shared location editing/deletion are
available, with history protections. See [management rules](docs/inventory-management.md).
See [frontend setup and checks](frontend/README.md).

## Privacy and storage

Private inventory is enforced by the API for other application users. The local
Docker setup already stores data on this computer in PostgreSQL. Inventory fields
are not encrypted by the application; server/database administrators and readable
backups are outside that API privacy boundary. Shared house locations are visible
to house members. See [security review and storage options](docs/security-and-storage.md)
for the verified controls, limits and owner-only encryption considerations.

## Getting Started

### Prerequisites

- For the full local demo: Docker with Compose. Build tools run inside the image.
- For API development outside Docker: Java 21, Maven Wrapper and PostgreSQL 16.
- For frontend development: a supported Node version; see [frontend setup](frontend/README.md).

### Option 1 — Run locally

1. Create the database:

```bash
psql -U your_username postgres
CREATE DATABASE storage_manager;
\q
```

2. Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
   `SPRING_DATASOURCE_PASSWORD` in your local environment. Do not put real
   credentials into the tracked application.yaml.

3. Set a local `JWT_SECRET` environment variable with at least 32 characters.

4. Run the application:

```bash
./mvnw spring-boot:run
```

### Option 2 — Run with Docker

1. Copy the environment template:

```bash
cp .env.example .env
```
2. Replace the placeholder values in `.env`.

3. Start the services:

```bash
docker compose up --build
```

This builds Angular into the Java application and starts it with PostgreSQL,
waiting for the database to be ready. Open http://localhost:8080 for the web app;
Swagger is at http://localhost:8080/swagger-ui/index.html. No separate Node server
is needed to run this image. Both ports are bound to localhost; override
API_PORT/DB_PORT as needed. See [packaging and routing](docs/docker-web.md).
The storage-data volume keeps database contents across container recreation.

To check the stack using an isolated disposable database and synthetic data:

```bash
python3 scripts/verify_docker.py
```

This verifies the packaged web, routes/assets, non-root runtime, PostgreSQL schema updates, privacy, consumption, concurrent writes,
rollback and persistence after container recreation without using your local .env
or database. See [Docker/PostgreSQL verification](docs/docker-verification.md).

### Access the API

- API base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## API Endpoints

### Authentication (public)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive JWT token |

### Houses (requires token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/houses` | Create a house: `{"name":"Casa Valencia"}` |
| GET | `/api/houses` | List houses created by or shared with the authenticated user |
| GET | `/api/houses/{id}` | Read a house as its creator or member |
| POST | `/api/houses/{id}/invitation` | Creator generates or renews the invitation code |
| POST | `/api/houses/join` | Join using `{"code":"..."}` |

The owner is taken from the authenticated account, never from the request body.
Creation returns `201`, a `Location` header and `{id, name, ownerId}`.
Missing or blank names return `400`. Missing and inaccessible houses both return
`404`, including for users with the global ADMIN role.

Only the creator can generate or renew an invitation. Codes are returned once,
are reusable until renewed, and are stored as SHA-256 hashes. Renewal invalidates
the previous code without removing existing members. Joining again is harmless.

Locations belong to houses and support nesting. Items are private by default or
shared with their house. There is no mobile UI yet.

### Locations (requires token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/locations?houseId=1` | List locations in an accessible house |
| GET | `/api/locations/{id}` | Get location by ID |
| POST | `/api/locations` | Create a location |
| GET | `/api/locations/{id}/contents` | Preview direct active contents |
| POST | `/api/locations/{id}/move-contents` | Move direct active contents atomically |
| PUT | `/api/locations/{id}` | Update a location |
| DELETE | `/api/locations/{id}` | Delete a location |

Location creation and updates require `houseId` and `name`, with optional
`description` and `parentId` (null for a root). Example:
`{"houseId":1,"name":"Cajón 2","parentId":3}`.
Responses include `id`, `name`, `description`, `houseId`, `parentId` and the full
`path`, such as `Despacho → Cajonera → Cajón 2`. Creators and members may organize
their house's locations. Cycles and changing a location's house are rejected;
deleting locations with children, items or history returns `409`.
Legacy locations without a house are preserved and excluded from location access.
See [location contract](docs/house-locations.md) and
[bulk content transfers](docs/location-contents.md).

### Items (requires token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/items` | List accessible items across own houses (supports filters) |
| GET | `/api/items/{id}` | Get item by ID |
| POST | `/api/items` | Create an item |
| PUT | `/api/items/{id}` | Update an item |
| DELETE | `/api/items/{id}` | Delete an item |
| PATCH | `/api/items/{id}/move` | Move item to another location |
| GET | `/api/items/{id}/history` | Get movement history |
| POST | `/api/items/{id}/consume` | Consume units: `{"quantity":5}` |
| GET | `/api/items/{id}/consumptions` | Get consumption history |
| POST | `/api/items/{id}/uses` | Take reusable objects for use: `{"quantity":1}` |
| GET | `/api/items/{id}/uses` | Get usage and return history |
| POST | `/api/items/{id}/uses/{useId}/return` | Return a complete recorded withdrawal |

**Available filters for** `GET /api/items`:

- `?search=lipo` — search by name (case-insensitive)
- `?category=Battery` — filter by category
- `?locationId=1` — filter by location
- `?houseId=1` — filter by an accessible house

Filters can be combined: `?search=lipo&category=Battery`.

Create items with `name`, `quantity`, `locationId` and optional `description`,
`category`, `visibility` (`PRIVATE` by default or `HOUSE_SHARED`). The owner comes
from the session. Owners can edit/delete; house members can read and move shared
items. House creators and global ADMIN accounts cannot access others' private items.
Privacy applies to lists, search, ID, changes, movement and history.

Responses include `ownerId`, `visibility` and location with its full `path`.
Moves require the entire quantity and a different location in the same house;
location and history are saved atomically, including zero-stock records. PUT cannot
change location. Owners can permanently delete active or archived items together
with their history; active uses must be returned first. See [deletion rules](docs/item-deletion.md).
Legacy unassigned items remain stored and
hidden. See [inventory privacy and movement contract](docs/item-privacy.md).

Consumption reduces stock without moving the item and records the session user,
timestamp, quantity and remaining stock. Insufficient stock returns `409`.
Zero-stock items remain searchable with `outOfStock: true`; quantity can be zero
but never negative. Consumption history follows the same privacy rules as the item.
See [consumption contract](docs/item-consumption.md).

### Users (requires token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users/me` | Read the authenticated user's profile |
| GET | `/api/users/{id}` | Read own profile by ID |

Profiles expose only `id`, `username`, `email` and `role`. Another account and an
unknown account both return `404`, including for global ADMIN accounts.
Account listing and deletion are disabled (`403`). Account deletion will require
an explicit lifecycle for owned houses, memberships and inventory before it can
be enabled safely. See [account access](docs/account-access.md).

## Authentication Flow

1. Register a user:

```json
POST /api/auth/register
{
    "username": "viewer",
    "email": "viewer@example.com",
    "password": "your_password"
}
```

2. Login to receive a token:

```json
POST /api/auth/login
{
    "email": "admin@example.com",
    "password": "your_password"
}
```

3. Use the token in subsequent requests:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Protected endpoints return `401` with `WWW-Authenticate: Bearer` and a generic
JSON error when authentication is missing or the token is invalid/expired.
Public login remains available with an old invalid Bearer token. See
[JWT authentication behavior](docs/authentication.md).

Registration validates username/email and passwords (at least 8 characters, at most
72 UTF-8 bytes). New emails are stored lowercase; login is case-insensitive and uses
the account's actual email in its JWT. Passwords are never trimmed or truncated.
Duplicate accounts return a generic `409`; wrong credentials return a generic `401`.
Unexpected runtime failures return `500` without internal details. See the
[authentication contract](docs/authentication.md).

Login is limited to 20 requests/minute and registration to 10 requests/15 minutes,
per connection address, with separate quotas. Exceeding a quota returns `429` and
`Retry-After`. Forwarding headers from clients are not trusted. Counters are bounded,
in-memory and reset on restart; see [rate limits and proxy considerations](docs/auth-rate-limits.md).

## Project Structure

```
src/main/java/com/brahyam/storagemanager/
├── config/          → Security, JWT, error handling
├── controller/      → REST endpoints
├── dto/             → Request/response objects
├── entity/          → Database models
├── repository/      → Data access layer
├── service/         → Business logic
└── StorageManagerApplication.java
```

## Design Decisions

- **Monolithic architecture** — the project scope doesn't justify the complexity of microservices. The layered structure (controller → service → repository) keeps the code organized and makes it straightforward to refactor into separate services if needed in the future.
- **Single location per item** — items are assigned to one location at a time. Splitting quantities across multiple locations would add significant complexity without proportional benefit at this stage. Planned as a future enhancement.
- **Movement history as a separate entity** — instead of just updating the item's location, every movement is recorded with origin, destination, quantity, and timestamp. This provides a complete audit trail.
- **Explicit API DTOs** — validated request DTOs and dedicated house, location, item, movement and profile responses keep persistence relations out of the API. Registration also returns a dedicated profile DTO without password or hash.

History timestamps include UTC offsets and display in the browser’s local zone;
see [timestamp compatibility](docs/history-timestamps.md).

## Verification

Run all checks locally with `./scripts/verify_local.sh`. See
[local verification and optional CI](docs/verification.md). The GitHub workflow is
manual only and has no deployment steps.


Run the Java suite with `mvn test` (Java 21). Mockito instrumentation is configured
by Maven; no machine-specific agent path is required. The current suite has 122 tests,
including OpenAPI schema and Swagger UI checks. Docker verification also checks
the packaged documentation endpoints.

For a dated OSV check of the resolved runtime dependency graph:

```bash
mvn dependency:tree -Dscope=runtime -DoutputType=json -DoutputFile=target/dependencies.json
python3 scripts/check_dependencies.py target/dependencies.json
```

See [dependency review and scope](docs/dependency-review.md). A clean catalog result
is not a complete security audit of the application, image or deployment.

## Future Improvements

- Review account deletion lifecycle and additional administration requirements
- Multi-location item support with quantity per location
- Image attachments for items
- Export inventory to CSV/PDF
- OAuth2 login (Google)
- Complete Angular forms for inventory, locations, consumption and history
