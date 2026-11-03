# Crear y consultar una casa: primera entrega

Como usuario autenticado quiero crear una casa con un nombre y consultar mis
casas, para empezar a organizar mis objetos.

## Criterios de aceptación

- Al crear una casa con un nombre válido, se guarda con el usuario de la sesión
  como propietario. La respuesta es 201 y contiene el identificador de la casa.
- Si el nombre falta, es nulo o solo contiene espacios, se devuelve 400 y no se
  guarda ninguna casa.
- Un usuario solo puede listar y consultar las casas que crea o a las que se une. Una consulta ajena o a una
  casa inexistente devuelve el mismo 404. El rol global ADMIN no evita este filtro.
  Las peticiones sin sesión no tienen acceso.

## Contrato

- POST /api/houses: cuerpo {"name":"Casa Valencia"}.
- GET /api/houses: lista de casas accesibles para el usuario autenticado.
- GET /api/houses/{id}: casa accesible o 404.
- Respuesta de casa: id, name y ownerId; no se serializa la entidad User.
- No se acepta un propietario elegido por el cliente; los campos adicionales
  no cambian el usuario tomado de la sesión.
- Se eliminan espacios al principio y al final del nombre.

## Límites de esta entrega

La membresía y las invitaciones se incorporan en `house-membership.md`. No se
impone un límite de una casa por usuario. Las entregas posteriores
`house-locations.md` e `item-privacy.md` vinculan ubicaciones e inventario a casas
y protegen la privacidad.
La interfaz móvil también está pendiente. Las pruebas de API usan JWT, Spring
Security y H2; todavía no verifican PostgreSQL ni Docker.
