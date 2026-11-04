# Ubicaciones por casa y jerarquía

Como miembro quiero organizar ubicaciones por niveles para encontrar dónde guardo
mis cosas: «Despacho → Cajonera → Cajón 2».

## Contrato

- GET `/api/locations?houseId=1`: ubicaciones de una casa accesible, ordenadas por ID.
- GET `/api/locations/{id}`: una ubicación accesible.
- POST `/api/locations`: crea una ubicación; devuelve 201 y Location.
- PUT `/api/locations/{id}`: modifica nombre, descripción y padre, conservando casa.
- DELETE `/api/locations/{id}`: retira solo la ubicación; devuelve204. Sus objetos quedan sin
  ubicación y sus sububicaciones pasan a la raíz. [Contrato actual](location-deletion.md).
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

El borrado admite hijos, objetos e historial, sin eliminar esos datos. La fila
permanece únicamente como referencia histórica, excluida de las ubicaciones usables.
No hay endpoints para borrar casas o miembros.

## Datos anteriores y alcance

`house_id` se mantiene nullable para conservar ubicaciones anteriores al modelo de
casas. La API exige casa para registros nuevos y no expone ubicaciones antiguas sin
asignación. No se atribuyen datos existentes automáticamente a una cuenta. Antes de
usar datos reales se necesita una asignación explícita, revisada y con copia de
seguridad. La compatibilidad actual se describe en [eliminación de ubicaciones](location-deletion.md).

La privacidad del inventario y los historiales se describe en [item-privacy.md](item-privacy.md).
Las escrituras comparten el bloqueo de casa y se verifican con las suites Java y
PostgreSQL aislado, incluyendo rollback y persistencia tras recrear contenedores.
