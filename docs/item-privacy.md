# Inventario por casa y privacidad

## Modelo y permisos

La casa de un objeto es la de su ubicación. El propietario se toma de la sesión y
no puede transferirse desde el cuerpo de una petición. Un objeto nuevo es PRIVATE
si no se especifica visibilidad; HOUSE_SHARED lo comparte con los miembros.

| Operación | Propietario miembro | Otro miembro, objeto compartido | Objeto privado ajeno o cuenta externa |
|---|---|---|---|
| Ver/buscar e historial | Permitido | Permitido | Oculto; ID e historial devuelven 404 |
| Mover | Permitido | Permitido | 404 |
| Editar, cambiar visibilidad o borrar | Permitido | 403 | 404 |

El creador de la casa y ADMIN no tienen privilegios especiales sobre objetos
privados. Es necesario seguir teniendo acceso a la casa, también siendo propietario
del objeto. Las ubicaciones siguen siendo visibles aunque su contenido sea privado.
La visibilidad actual gobierna todo el historial: al volver privado un objeto se
ocultan tanto el objeto como sus movimientos anteriores a los demás miembros.

## Contrato

- POST `/api/items`: name, quantity (mínimo 0), locationId; opcionales description,
  category y visibility (PRIVATE/HOUSE_SHARED). Devuelve 201, Location y el objeto.
- GET `/api/items`: objetos accesibles, ordenados por ID. Filtros combinables:
  houseId, search (nombre sin distinguir mayúsculas), category y locationId.
  Sin houseId se buscan los objetos accesibles en todas las casas del usuario.
- GET `/api/items/{id}`: objeto accesible; un ID oculto o inexistente devuelve el
  mismo 404 y mensaje «Objeto no encontrado».
- PUT `/api/items/{id}`: solo propietario, misma ubicación. Omitir visibility
  conserva la actual. Para cambiar ubicación hay que usar el movimiento.
- PATCH `/api/items/{id}/move?toLocationId=2&quantity=3`: mueve la cantidad completa
  a una ubicación diferente de la misma casa. No reparte cantidades ni traslada
  entre casas. Peticiones inválidas no generan historial.
- GET `/api/items/{id}/history`: movimientos accesibles, más recientes primero.
- DELETE `/api/items/{id}`: solo propietario; 204 si no hay historial, 409 si hay movimientos o consumos.
  Se conserva la auditoría; no se borra en cascada.

Las respuestas de objeto contienen id, name, description, category, quantity,
ownerId, visibility, outOfStock y location (con houseId, parentId y path completo). El historial
contiene id, fromLocation, toLocation, quantity y movedAt. Ninguna respuesta incluye
entidades User ni referencias al objeto dentro de cada movimiento. JSON malformado
y visibilidad desconocida reciben 400 genérico sin detalles del deserializador.

## Transacciones y datos anteriores

Crear, editar, borrar y mover objetos bloquea la casa durante la transacción, usando
el mismo bloqueo que las escrituras de ubicaciones. Se comprueba de nuevo el acceso
al objeto tras obtener el bloqueo. Movimiento e historial se guardan juntos; si una
escritura falla se revierten ambos. Se serializan las escrituras de una misma casa;
es una solución sencilla para este alcance, con posible contención en casas grandes.

owner_id y visibility admiten nulos en la tabla para conservar registros legacy.
La API siempre asigna propietario y visibilidad a registros nuevos. Los registros
sin propietario, casa o visibilidad quedan fuera de los accesos de la API; no se
reclaman automáticamente. Antes de asignar datos antiguos hay que revisar también
sus ubicaciones e historial, con copia de seguridad. No se ha migrado ni modificado
la base PostgreSQL del usuario desde esta sesión.

## Verificación y límites

Doce tests de API cubren las rutas y filtros, propietario, creador ADMIN, miembros,
cuenta externa, cambios de visibilidad, historial y registros antiguos. En una copia
del commit anterior fallaron once; en la nueva versión pasan todos.
Dos tests sin transacción exterior comprueban rollback tras escritura forzada y dos
movimientos HTTP concurrentes con una cadena de historial consistente. Suite total:
65 tests sin fallos, errores ni omisiones, con H2 y agente Mockito.

Pendientes: repetir integración/concurrencia en PostgreSQL, Docker, validación de
registro/login, manejo general de errores, revisión de dependencias y frontend.
Esto no equivale a una auditoría de seguridad completa ni a una demo desplegada.

## Consumos

La entrega `item-consumption.md` añade descuento de unidades sin movimiento y su
historial protegido. Los miembros pueden consumir compartidos y el dueño sus
privados. Cantidad cero conserva el objeto con outOfStock; no se mueve stock cero.
