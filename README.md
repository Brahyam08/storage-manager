# Storage Manager API

REST API for managing physical inventory across storage locations. Built to solve a real problem: tracking where items like drone parts, batteries, controllers, and other gear are stored across multiple drawers and compartments — so you never have to tear apart a room looking for something again.

## Tech Stack

- **Java 21** + **Spring Boot 3.x**
- **PostgreSQL 16**
- **Spring Security** with JWT authentication
- **Spring Data JPA** with Hibernate
- **Swagger/OpenAPI** for interactive documentation
- **Docker** + Docker Compose
- **Lombok** for boilerplate reduction
- **BCrypt** for password hashing

## Features

- Full CRUD for locations, items, and users
- JWT-based authentication with role support (ADMIN / VIEWER)
- Item movement tracking with full history — every time an item is moved between locations, the system records where it came from, where it went, the quantity moved, and the timestamp
- Search and filtering — find items by name, category, or location
- Automatic API documentation via Swagger UI
- Global error handling with meaningful HTTP status codes
- Input validation on all request bodies
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

2. Update `src/main/resources/application.yml` with your database credentials.

3. Run the application:

```bash
mvn spring-boot:run
```

### Option 2 — Run with Docker

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

### Locations (requires token)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/locations` | List all locations |
| GET | `/api/locations/{id}` | Get location by ID |
| POST | `/api/locations` | Create a location |
| PUT | `/api/locations/{id}` | Update a location |
| DELETE | `/api/locations/{id}` | Delete a location |

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
    "username": "admin",
    "email": "admin@example.com",
    "password": "your_password",
    "role": "ADMIN"
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