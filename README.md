# Storage Manager API

REST API for managing physical inventory across storage locations. Built to solve a real problem: tracking where items like drone parts, batteries, controllers, and other gear are stored across multiple drawers and compartments — so you never have to tear apart a room looking for something again.

## Tech Stack

- **Java 21** + **Spring Boot 4.x**
- **PostgreSQL 16**
- **Spring Security** with JWT authentication
- **Spring Data JPA** with Hibernate
- **Swagger/OpenAPI** for interactive documentation
- **Docker** + Docker Compose
- **Lombok** for boilerplate reduction
- **BCrypt** for password hashing

## Features

- Full CRUD for locations and items, with user queries and deletion
- JWT-based authentication; public registrations receive the VIEWER role
- Item movement tracking with full history — every time an item is moved between locations, the system records where it came from, where it went, the quantity moved, and the timestamp
- Search and filtering — find items by name, category, or location
- Automatic API documentation via Swagger UI
- Centralized error handling for API responses
- Input validation for location and item requests
- Dockerized for easy deployment

## Getting Started

### Prerequisites

- Java 21
- Maven
- PostgreSQL 16
- Docker (optional)

### Option 1 — Run locally

1. Create the database:

```bash
psql -U your_username postgres
CREATE DATABASE storage_manager;
\q
```

2. Update `src/main/resources/application.yaml` with your database credentials.

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

This starts both the API and a PostgreSQL instance. No local database setup needed.

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

Locations now belong to houses and support nesting. Existing item reads are
still global and private objects are not implemented yet. There is no mobile UI yet.

### Locations (requires token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/locations?houseId=1` | List locations in an accessible house |
| GET | `/api/locations/{id}` | Get location by ID |
| POST | `/api/locations` | Create a location |
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
See [location contract](docs/house-locations.md).

### Items (requires token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/items` | List all items (supports filters) |
| GET | `/api/items/{id}` | Get item by ID |
| POST | `/api/items` | Create an item |
| PUT | `/api/items/{id}` | Update an item |
| DELETE | `/api/items/{id}` | Delete an item |
| PATCH | `/api/items/{id}/move` | Move item to another location |
| GET | `/api/items/{id}/history` | Get movement history |

**Available filters for** `GET /api/items`:

- `?search=lipo` — search by name (case-insensitive)
- `?category=Battery` — filter by category
- `?locationId=1` — filter by location

Filters can be combined: `?search=lipo&category=Battery`

### Users (requires token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | List all users |
| GET | `/api/users/{id}` | Get user by ID |
| DELETE | `/api/users/{id}` | Delete a user |

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
- **DTOs for input, entities for output** — request bodies use dedicated DTOs with validation annotations, keeping the API contract separate from the database model. Passwords are excluded from responses using `@JsonIgnore`.

## Future Improvements

- Role-based endpoint restrictions (ADMIN vs VIEWER permissions)
- Multi-location item support with quantity per location
- Image attachments for items
- Export inventory to CSV/PDF
- OAuth2 login (Google)
- Frontend client (React or React Native)
