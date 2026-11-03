# Consumibles y objetos reutilizables

`kind` expresa el uso del objeto; no se deduce de su nombre ni de la cantidad.

- `CONSUMABLE`: existencias que disminuyen al gastar unidades. Puede tener cantidad
  cero; `outOfStock` es verdadero únicamente para consumibles agotados.
- `REUSABLE`: objetos que se conservan al usarlos (ratones, taladros, mandos).
  Puede representar uno o varios ejemplares, siempre al menos uno. No admite
  consumo; la API devuelve 409 sin modificar cantidad ni historial.

Crear o editar acepta `kind`. Las peticiones antiguas que lo omiten crean un
consumible o conservan el tipo actual al editar. Solo el propietario puede
reclasificar. No se admite pasar de consumible a reutilizable si existen consumos:
el historial conserva su significado. La propiedad, visibilidad y permisos de
movimiento se mantienen. `kind` no concede acceso a objetos privados.

Angular permite elegir el tipo, filtrar por él y muestra los reutilizables como
objetos registrados; no ofrece acciones de consumo ni historial de consumos para
ellos. La gestión de [uso y devolución](reusable-usage.md) distingue objetos
disponibles y retirados, conservando el total y la ubicación habitual.

## Compatibilidad de los datos

La columna `items.kind` es no nula y usa el valor SQL predeterminado `CONSUMABLE`.
La actualización de esquema actual de Hibernate añade esa columna sin asignar
usuarios/casas a filas antiguas ni alterar sus cantidades. Los datos anteriores
mantienen el comportamiento previo hasta una reclasificación explícita.

Se verificó una actualización PostgreSQL desde la imagen anterior con tres objetos
y cantidades existentes. `scripts/verify_docker.py` también comprueba esquema
legacy, tipo predeterminado, rechazo de consumo reutilizable y preservación tras
tres recreaciones. La suite Java incluye creación, reclasificación, permisos,
validación y protección del historial.

Antes de actualizar datos importantes, conservar una copia de PostgreSQL. Sigue
pendiente adoptar migraciones versionadas para producción; esta entrega no las
sustituye. La cantidad editable sigue siendo un ajuste explícito mediante PUT sin
versionado optimista; el consumo usa la operación atómica específica.
