# Consumo de unidades y objetos sin existencias

Ejemplo: hay diez tornillos en un cajón. Consumir cinco deja cinco en ese mismo
cajón y registra quién los gastó y cuándo. Consumir no es mover objetos.

## Contrato

POST `/api/items/{id}/consume` con `{"quantity":5}` devuelve 200 y el objeto
actualizado. quantity debe ser un entero positivo. Se devuelve 400 si falta,
es nulo, decimal, cero o negativo; 409 si supera las unidades disponibles.

GET `/api/items/{id}/consumptions` devuelve el historial de consumos, más recientes
primero: id, quantity, remainingQuantity, consumedById, consumedAt y location.
El actor proviene de la sesión; no se acepta uno elegido por el cliente.
Los DTOs no exponen entidades User, correos ni hashes de contraseña. La configuración
JSON rechaza decimales en campos enteros para evitar truncar cantidades.

El historial de movimientos conserva su endpoint GET `/api/items/{id}/history`.
Son registros separados: el consumo no genera movimientos ni cambia de ubicación.

## Existencias y permisos

- Un objeto puede quedar en cero: se conserva y sigue apareciendo en búsquedas.
- La respuesta del objeto incluye outOfStock, verdadero cuando quantity es cero.
- Se admiten objetos nuevos y edición de objetos con quantity cero; nunca negativo.
- El propietario puede consumir un privado. Cualquier miembro puede consumir
  compartidos de su casa. Creador de casa y ADMIN no evitan la privacidad.
- El historial de consumos sigue la visibilidad actual del objeto, igual que los
  movimientos. Una cuenta externa o privado ajeno/inexistente recibe el mismo 404.
- Borrar un objeto con consumos devuelve 409. Se conservan también las ubicaciones
  referenciadas por el historial; no hay borrado en cascada.

El consumo y su registro comparten transacción y el bloqueo de casa usado por
movimientos, ediciones y ubicaciones. Dos consumos simultáneos no pueden descontar
las mismas unidades. Si falla el guardado se revierten cantidad y registro.

PUT sigue permitiendo al propietario corregir la cantidad absoluta: esa corrección
no se registra como consumo. La reposición con historial propio queda fuera de esta
entrega. Cada POST consume unidades; repetir voluntariamente la petición registra
otro consumo. La interfaz debe evitar envíos duplicados.

## Verificación

Ocho tests HTTP cubren 10→5, agotamiento, búsquedas, actor de sesión, cantidades
inválidas, privacidad, miembros, conservación del historial y peticiones anónimas.
Dos pruebas sin transacción exterior fuerzan un fallo después de escribir y ejecutan
dos consumos simultáneos sobre existencias insuficientes. Suite: 75 tests sin fallos,
errores ni omisiones, con H2 y agente Mockito. La verificación HTTP posterior de PostgreSQL/Docker
en `docker-verification.md` comprueba consumo, concurrencia, rollback y persistencia.
