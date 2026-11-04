# Eliminar un objeto definitivamente

Eliminar y archivar son acciones independientes. El propietario puede eliminar
un objeto activo o archivado, tenga o no movimientos, consumos, reposiciones o
usos anteriores. No hay que restaurarlo primero, tampoco si su ubicación está
archivada. La ubicación conserva su propio estado.

La confirmación explica que se eliminan el objeto y todo su historial asociado.
No hay papelera ni recuperación desde la interfaz. Para conservar los registros,
usar Archivar. Las copias de seguridad previas son independientes: no se purgan
al eliminar un objeto de la base actual.

Se mantiene una excepción: no se elimina mientras haya piezas en uso. Deben
registrarse las devoluciones antes. Evita perder el seguimiento de un préstamo
pendiente. La interfaz bloquea la acción y la API también la valida bajo bloqueo.

DELETE /api/items/{id} devuelve204 al eliminar. Si hay uso activo,409 sin cambios.
Un miembro lector de un compartido recibe403; privado ajeno/inexistente404, incluso
ADMIN global o creador de la casa. La comprobación de propiedad ocurre antes de
borrar historial. No se amplían permisos por estar archivado.

## Atomicidad y alcance

Se bloquea la casa como en consumo/uso/movimiento, comprueba el propietario y el
uso activo, elimina solo filas cuyo item_id coincide en item_movements,
item_consumptions,item_restocks,item_uses, y después el objeto. Es una transacción:
si falla el borrado, se conservan tanto objeto como sus historiales.

No se borran usuarios, ubicaciones ni registros de otros objetos. El borrado de
ubicaciones mantiene sus restricciones de hijos, objetos y referencias históricas.
Si ya no queda ninguna referencia, una ubicación vacía puede eliminarse según
esas reglas; no se borra automáticamente al eliminar su último objeto.

## Verificación

153 tests Java y83 Angular y build. PostgreSQL aislado comprueba eliminación con
consumo/reposición/movimiento, archivado, rollback por fallo forzado al borrar,
historial de otro objeto intacto y concurrencia entre borrar y retirar para usar.
Los borrados reales de pruebas se realizan solo en fixtures desechables. En la
UI de revisión se comprueba la confirmación y se cancela; no se elimina inventario
del usuario. El cambio no requiere nuevas columnas o dependencias.
