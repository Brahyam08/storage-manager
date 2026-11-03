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
- Create nested locations and objects with quantity and private/shared visibility.
- Read inventory, search by name and filter private/shared/out-of-stock items.
- Location paths, quantities, loading/errors/empty states and mobile layout.

Editing/deleting objects and locations, consumption, movement and histories still
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

18 tests cover creation, authentication, HTTP isolation, stale responses, password byte
limits, partial registration success and house/invitation races. Production build
passes. Browser checks with isolated Docker/PostgreSQL covered registration/login,
create/join/invite, search/filter, empty houses, private-item exclusion for another
member, reload logout and mobile layout. Creation checks also covered nested locations,
empty-house onboarding, zero-stock shared objects, private defaults and mobile submission. The unchanged backend separately has
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
