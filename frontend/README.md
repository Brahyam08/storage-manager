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
- Move all units to another location in the same house and inspect movement history.
- Location paths, quantities, loading/errors/empty states and mobile layout.

Deleting objects and editing/deleting locations still need frontend forms. Those operations are available through the API. There is no
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

32 tests cover editing, consumption/history, creation, authentication, HTTP isolation, stale responses, password byte
limits, partial registration success and house/invitation races. Production build
passes. Browser checks with isolated Docker/PostgreSQL covered registration/login,
create/join/invite, search/filter, empty houses, private-item exclusion for another
member, reload logout and mobile layout. Creation checks also covered nested locations,
empty-house onboarding, zero-stock shared objects, private defaults and mobile submission. Consumption checks used 10 → 5 → 0 units,
verified persisted history, disabled consumption at zero, and owner editing. The unchanged backend separately has
106 passing tests.

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
visibility and current stock (an explicit adjustment). Editing retains the location; use **Mover objeto** to change it. Reusable objects have at least one exemplar and no consumption actions. A single
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


## Select controls

Closed selects share the same arrow, border and spacing, including the house
selector. On desktop browsers supporting `appearance: base-select`, the option
menu also uses the app colors, selection state and rounded edges through a
[CSS enhancement](https://developer.chrome.com/blog/a-customizable-select).
Other browsers and touch devices retain their native option picker. Forced-color
mode keeps the native control. No JavaScript selection component was introduced.

Verified in the preview browser: open menu, keyboard option navigation/confirmation,
pointer selection, location options and mobile-width layout without overflow.
Production build passes. Native picker appearance remains platform-dependent in
browsers without the enhancement; Safari and physical touch devices were not tested.


## Movement

**Mover objeto** loads destinations from the current house and excludes the current
location. It sends the full current quantity, keeping one location per inventory
entry. The server enforces house access, full quantity, privacy and movement history;
shared items can be moved by house members. Zero-stock items cannot be moved under
the current API contract. Create another location if no destination is available.

After success, the inventory and detail use the server location. If stock changed,
the API rejects the stale quantity; refresh the item before retrying. Destination
selection remains available after errors. Pending destinations, movements and
histories are cancelled when the selected item/house changes. Cancelling a request
cannot undo a movement already accepted by the server.

**Ver movimientos** shows full origin/destination paths, quantities and recorded
dates, including empty/loading/error states. Browser verification against isolated
PostgreSQL covered moving ten consumable units, a reusable item on mobile, the
unchanged quantities, updated paths and both histories. Unit tests additionally
cover member actions, forbidden destinations, zero stock, duplicate submit, stale
stock and cancelled responses. Java code and dependencies are unchanged in this
delivery; the backend suite previously passed 106 tests.
