# Eliminar una ubicación sin eliminar objetos

**Gestionar ubicaciones → Eliminar** permite retirar una ubicación activa o
archivada aunque tenga objetos, sububicaciones o historial. Una confirmación
explica el efecto antes de enviar `DELETE /api/locations/{id}`.

- Los objetos guardados directamente allí quedan con `location: null`.
- Conservan `houseId`, propietario, privacidad, cantidades, archivo e historiales.
- Las sububicaciones directas pasan a la raíz de la misma casa. Sus objetos
  permanecen dentro de ellas; no se vacía todo el árbol.
- No se elimina ningún objeto ni se devuelve automáticamente ninguna pieza en uso.
- La ubicación desaparece de listas activas y archivadas. Consultarla, usarla como
  destino, modificarla o restaurarla devuelve404.

La UI muestra **Sin ubicación**, permite filtrar esos objetos y asignarles destino
con **Mover objeto**. Pueden editarse, consumirse, reponerse, retirarse/devolverse y
archivarse/restaurarse normalmente, respetando los permisos anteriores. Crear un
objeto sigue requiriendo elegir una ubicación; editar uno sin ubicación acepta
`locationId: null`. PUT no cambia la ubicación; para asignarla se usa el movimiento.

## Historial y almacenamiento

El borrado es lógico: se conserva la fila de ubicación con `deleted=true` y su
última ruta (`deletedPath`) exclusivamente como referencia de los historiales.
No existe restauración de ubicaciones eliminadas. Las respuestas históricas
incluyen `deleted:true` y la ruta con «(eliminada)». Los eventos nuevos de un objeto
sin ubicación devuelven `location:null`; al moverlo, `fromLocation:null`.
No se reescriben los destinos anteriores ni se inventan movimientos al borrar.
Esto conserva nombres y descripciones en la base local; no es una purga de datos.

Los miembros de la casa pueden gestionar sus ubicaciones. El borrado también
retira la ubicación de objetos privados ajenos sin revelar su contenido ni dar
acceso a ellos. Las reglas de privacidad de cada objeto se mantienen; usuarios
externos, incluso ADMIN, reciben404. Todo se hace bajo el bloqueo de casa en una
transacción: si falla, tampoco se desvinculan objetos o sububicaciones.

## Compatibilidad de datos

`items.house_id` independiza la casa de la ubicación. Al arrancar, una migración
compatible con PostgreSQL/H2 copia la casa ya existente de la ubicación para las
filas anteriores que no la tengan. No asigna datos antiguos sin casa a una cuenta.
También permite nulos en los campos de ubicación de objetos y nuevos eventos.
Es idempotente y conserva las claves externas y los datos anteriores. La
aplicación necesita permisos de esquema, como antes para `ddl-auto=update`.
Antes de actualizar la base local se realiza una copia privada fuera del repo.

## Verificación

Java y Angular cubren inventario sin ubicación, privacidad, historial, archivo,
uso/devolución, edición, reubicación y confirmación. PostgreSQL aislado comprueba
restricciones antiguas NOT NULL, fallo forzado con rollback, eventos sin ubicación,
sububicaciones y persistencia tras recrear los contenedores tres veces.
