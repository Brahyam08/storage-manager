# Storage Manager web

Angular 21 standalone client with reactive forms, signals and RxJS.

## Local setup

Use Node 20.19+, 22.12+ or 24.x (tested: 24.14.0). Start the backend on port
8080 using the root README, then run from this directory:

```bash
npm ci
npm start
```

Open http://localhost:4200. The loopback development server proxies `/api/**` to
`http://127.0.0.1:8080`. No backend CORS changes are required. The backend Docker
image does not package this frontend. Production hosting and HTTPS remain pending.
Angular 21 matches the installed Node version; consult the
[compatibility table](https://angular.dev/reference/versions) before upgrading.

## Delivered scope

- Registration, login, verified profile, logout and session expiration.
- Create/join houses; renew invitations as the house owner.
- Create nested locations and objects with quantity, private/shared visibility and
  explicit consumable/reusable type.
- Read inventory, search by name and filter private/shared/out-of-stock items.
- Open item details, edit as owner, record consumption and view consumption history.
- Location paths, quantities, loading/errors/empty states and mobile layout.

Deleting objects, editing/deleting locations, movement and movement history still
need frontend forms. Those operations are available through the API. There is no
bundled demo account.

## Session and privacy

JWT/profile exist only in memory. Refreshing requires signing in again. The
interceptor sends Bearer credentials only to relative `/api/` requests. Protected
401 responses clear the matching session; old failures cannot clear a new login.
Passwords are never trimmed; registration validates the BCrypt 72-byte UTF-8
limit. If automatic login fails after registration, the account remains usable.

House changes cancel previous inventory requests. Late invitation responses for
another house are ignored. Creation forms reset and pending requests are cancelled
on house changes. Cancellation does not roll back an operation already accepted
by the server; reopening the house reloads its current inventory. The backend enforces membership and private-item
access; UI filtering is presentation only.

## Verification

```bash
npm test
npm run build
npm audit
```

27 tests cover editing, consumption/history, creation, authentication, HTTP isolation, stale responses, password byte
limits, partial registration success and house/invitation races. Production build
passes. Browser checks with isolated Docker/PostgreSQL covered registration/login,
create/join/invite, search/filter, empty houses, private-item exclusion for another
member, reload logout and mobile layout. Creation checks also covered nested locations,
empty-house onboarding, zero-stock shared objects, private defaults and mobile submission. Consumption checks used 10 → 5 → 0 units,
verified persisted history, disabled consumption at zero, and owner editing. The unchanged backend separately has
101 passing tests.

Targeted Angular CLI overrides use glob 14.0.3 and minimatch 11.0.4 for
[GHSA-3g3m-2fjr-6pw4](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4) and
[GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249).
Reconsider these development dependency overrides when CLI updates its ranges.
Build and generator dry-run passed. npm audit reported zero advisories including
development dependencies on 2026-11-03; deployment security remains a separate check.

## Code map

- `src/app/core`: API models, authentication, interceptor, guard and errors.
- `src/app/auth`: registration/login and password validation.
- `src/app/inventory`: houses and inventory browser.
- `src/styles.scss`: responsive shared styles.


## Editing and consumption

Click an object's name to open its details. The owner can edit its details,
visibility and current stock (an explicit adjustment). Location is retained;
movement will have its own action. Reusable objects have at least one exemplar and no consumption actions. A single
screw can still be consumable; several mice can be reusable. Existing items retain
their previous consumable behavior until explicitly reclassified by their owner.
An item with recorded consumption cannot become reusable. Usage/return tracking
is still pending. See [type rules](../docs/item-kinds.md).

Use **Registrar consumo** for consumables when spending units:
it records the amount and remaining stock in the API history. A member can consume
shared items; only the owner can edit them. UI controls supplement API permissions.

The server response updates both the detail and inventory counters. Insufficient
stock returns an error without changing local quantities; **Actualizar objeto**
reloads current stock before retrying. Pending detail requests are cancelled on
selection/house changes. Cancellation cannot undo an already accepted operation.
Stock adjustments use the existing PUT contract without optimistic versioning;
refresh before adjusting stock when several members may be using the same item.
Consumption is the atomic backend operation for spending units.
