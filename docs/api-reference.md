# API reference

The normal local profile permits registration. In the closed beta, the server
rejects registration with 403; use provisioned test accounts. Swagger is disabled in
beta and available locally at `/swagger-ui/index.html`.

## Authentication (public)

| Method | Endpoint             | Description                                             |
| ------ | -------------------- | ------------------------------------------------------- |
| POST   | `/api/auth/register` | Register a VIEWER account; disabled in the beta profile |
| POST   | `/api/auth/login`    | Login and receive JWT token                             |

## Houses (requires token)

| Method | Endpoint                      | Description                                                  |
| ------ | ----------------------------- | ------------------------------------------------------------ |
| POST   | `/api/houses`                 | Create a house: `{"name":"Casa Valencia"}`                   |
| GET    | `/api/houses`                 | List houses created by or shared with the authenticated user |
| GET    | `/api/houses/{id}`            | Read a house as its creator or member                        |
| POST   | `/api/houses/{id}/invitation` | Creator generates or renews the invitation code              |
| POST   | `/api/houses/join`            | Join using `{"code":"..."}`                                  |

The owner is taken from the authenticated account, never from the request body.
Creation returns `201`, a `Location` header and `{id, name, ownerId}`.
Missing or blank names return `400`. Missing and inaccessible houses both return
`404`, including for users with the global ADMIN role.

Only the creator can generate or renew an invitation. Codes are returned once,
are reusable until renewed, and are stored as SHA-256 hashes. Renewal invalidates
the previous code without removing existing members. Joining again is harmless.

Locations belong to houses and support nesting. Items are private by default or
shared with their house. The Angular UI supports desktop and mobile layouts.

## Locations (requires token)

| Method | Endpoint                            | Description                                             |
| ------ | ----------------------------------- | ------------------------------------------------------- |
| GET    | `/api/locations?houseId=1`          | List locations in an accessible house                   |
| GET    | `/api/locations/{id}`               | Get location by ID                                      |
| POST   | `/api/locations`                    | Create a location                                       |
| GET    | `/api/locations/{id}/contents`      | Preview direct active contents                          |
| POST   | `/api/locations/{id}/move-contents` | Move direct active contents atomically                  |
| PUT    | `/api/locations/{id}`               | Update a location                                       |
| DELETE | `/api/locations/{id}`               | Delete only the location; retain its objects unassigned |
| PATCH  | `/api/locations/{id}/archive`       | Archive a location                                      |
| PATCH  | `/api/locations/{id}/restore`       | Restore a location                                      |

Location creation and updates require `houseId` and `name`, with optional
`description` and `parentId` (null for a root). Example:
`{"houseId":1,"name":"Cajón 2","parentId":3}`.
Responses include `id`, `name`, `description`, `houseId`, `parentId` and the full
`path`, such as `Despacho → Cajonera → Cajón 2`. Creators and members may organize
their house's locations. Cycles and changing a location's house are rejected;
deleting a location leaves its direct items unassigned in the same house and
promotes direct child locations to house roots. Historical references remain available;
see [location deletion](location-deletion.md).
Legacy locations without a house are preserved and excluded from location access.
See [location contract](house-locations.md) and
[bulk content transfers](location-contents.md).

## Items (requires token)

| Method | Endpoint                              | Description                                                                      |
| ------ | ------------------------------------- | -------------------------------------------------------------------------------- |
| GET    | `/api/items`                          | List accessible items across own houses (supports filters)                       |
| GET    | `/api/items/{id}`                     | Get item by ID                                                                   |
| POST   | `/api/items`                          | Create an item                                                                   |
| PUT    | `/api/items/{id}`                     | Update an item                                                                   |
| DELETE | `/api/items/{id}`                     | Permanently delete an item and its histories; active uses must be returned first |
| PATCH  | `/api/items/{id}/archive`             | Archive an item                                                                  |
| PATCH  | `/api/items/{id}/restore`             | Restore an item                                                                  |
| POST   | `/api/items/{id}/restocks`            | Add units with `{"quantity":5}`; unavailable for individual reusable objects     |
| GET    | `/api/items/{id}/restocks`            | Read replenishment history                                                       |
| PATCH  | `/api/items/{id}/move`                | Move item to another location                                                    |
| GET    | `/api/items/{id}/history`             | Get movement history                                                             |
| POST   | `/api/items/{id}/consume`             | Consume units: `{"quantity":5}`                                                  |
| GET    | `/api/items/{id}/consumptions`        | Get consumption history                                                          |
| POST   | `/api/items/{id}/uses`                | Take reusable objects for use: `{"quantity":1}`                                  |
| GET    | `/api/items/{id}/uses`                | Get usage and return history                                                     |
| POST   | `/api/items/{id}/uses/{useId}/return` | Return a complete recorded withdrawal                                            |

**Available filters for** `GET /api/items`:

- `?search=lipo` — search by name (case-insensitive)
- `?category=Battery` — filter by category
- `?locationId=1` — filter by location
- `?houseId=1` — filter by an accessible house

Filters can be combined: `?search=lipo&category=Battery`.

Create items with `name`, `kind` (`CONSUMABLE` or `REUSABLE`), `quantity`,
`locationId` and optional `description`,
`category`, `visibility` (`PRIVATE` by default or `HOUSE_SHARED`). The owner comes
from the session. `individual: true` identifies one reusable object with quantity 1;
reusable groups remain explicit even when they contain one unit. Owners can edit/delete; house members can read and move shared
items. House creators and global ADMIN accounts cannot access others' private items.
Privacy applies to lists, search, ID, changes, movement and history.

Responses include `ownerId`, `houseId`, `visibility` and nullable `location` with its full `path`.
Moves require the entire quantity and a different location in the same house;
location and history are saved atomically, including zero-stock records. PUT cannot
change location. Owners can permanently delete active or archived items together
with their history; active uses must be returned first. See [deletion rules](item-deletion.md).
Legacy unassigned items remain stored and
hidden. See [inventory privacy and movement contract](item-privacy.md).

Consumption reduces stock without moving the item and records the session user,
timestamp, quantity and remaining stock. Insufficient stock returns `409`.
Zero-stock items remain searchable with `outOfStock: true`; quantity can be zero
but never negative. Consumption history follows the same privacy rules as the item.
See [consumption contract](item-consumption.md).

## Users (requires token)

| Method | Endpoint          | Description                           |
| ------ | ----------------- | ------------------------------------- |
| GET    | `/api/users/me`   | Read the authenticated user's profile |
| GET    | `/api/users/{id}` | Read own profile by ID                |

Profiles expose only `id`, `username`, `email` and `role`. Another account and an
unknown account both return `404`, including for global ADMIN accounts.
Account listing and deletion are disabled (`403`). Account deletion will require
an explicit lifecycle for owned houses, memberships and inventory before it can
be enabled safely. See [account access](account-access.md).

## Authentication Flow

1. Register a user:

```json
POST /api/auth/register
{
    "username": "viewer",
    "email": "viewer@example.invalid",
    "password": "your_password"
}
```

2. Login to receive a token:

```json
POST /api/auth/login
{
    "email": "viewer@example.invalid",
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
[JWT authentication behavior](authentication.md).

Registration validates username/email and passwords (at least 8 characters, at most
72 UTF-8 bytes). New emails are stored lowercase; login is case-insensitive and uses
the account's actual email in its JWT. Passwords are never trimmed or truncated.
Duplicate accounts return a generic `409`; wrong credentials return a generic `401`.
Unexpected runtime failures return `500` without internal details. See the
[authentication contract](authentication.md).

Login is limited to 20 requests/minute and registration to 10 requests/15 minutes,
per connection address, with separate quotas. Exceeding a quota returns `429` and
`Retry-After`. Forwarding headers from clients are not trusted. Counters are bounded,
in-memory and reset on restart; see [rate limits and proxy considerations](auth-rate-limits.md).
