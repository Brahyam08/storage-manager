# Ubicaciones por casa y jerarquía

Como miembro quiero organizar ubicaciones por niveles para encontrar dónde guardo
mis cosas: «Despacho → Cajonera → Cajón 2».

## Contrato

- GET `/api/locations?houseId=1`: ubicaciones de una casa accesible, ordenadas por ID.
- GET `/api/locations/{id}`: una ubicación accesible.
- POST `/api/locations`: crea una ubicación; devuelve 201 y Location.
- PUT `/api/locations/{id}`: modifica nombre, descripción y padre, conservando casa.
- DELETE `/api/locations/{id}`: elimina una ubicación vacía; devuelve 204.
- Crear y actualizar requieren `name` y `houseId`; `description` y `parentId` son
  opcionales. Un padre nulo representa una raíz. Se recortan los espacios del nombre.
- Respuesta: `id`, `name`, `description`, `houseId`, `parentId`, `path`.
  La ruta se calcula al consultar y refleja cambios de nombre y de padre.

## Acceso e integridad

El creador y los miembros pueden consultar y organizar las ubicaciones de la casa.
Un extraño, incluso ADMIN, recibe 404. Todas las operaciones requieren sesión.
El padre debe pertenecer a la misma casa. No se permite cambiar de casa, asignarse
como padre ni elegir un descendiente como padre. Las escrituras de jerarquía se
serializan bloqueando la casa durante su transacción.

Una ubicación con hijos, objetos o referencias en historial devuelve 409 al borrar.
No se borran datos en cascada. No hay endpoints nuevos para borrar casas o miembros.

## Datos anteriores y alcance

`house_id` se mantiene nullable para conservar ubicaciones anteriores al modelo de
casas. La API exige casa para registros nuevos y no expone ubicaciones antiguas sin
asignación. No se atribuyen datos existentes automáticamente a una cuenta. Antes de
usar datos reales se necesita una asignación explícita, revisada y con copia de
seguridad. No se ha ejecutado ninguna migración contra PostgreSQL del usuario.

Los accesos a ubicaciones desde creación, edición y movimiento de objetos ya
reciben al usuario autenticado. Las consultas globales de objetos, su privacidad,
la atomicidad de movimientos y el historial protegido pertenecen a la siguiente
entrega. Todavía no se puede afirmar que todo el inventario esté aislado por casa.

## Verificación

Nueve pruebas de API con JWT real, Security y H2 verifican rutas, acceso de miembros,
aislación, cambios de padre/nombre, ciclos, borrado protegido, datos antiguos y
peticiones anónimas. Se adaptaron los cinco tests de LocationService al nuevo
contrato. No se verificaron PostgreSQL, Docker ni concurrencia real entre clientes.
