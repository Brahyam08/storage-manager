# Archivar y restaurar objetos y ubicaciones

Archivar retira un registro del uso habitual sin borrar sus datos ni historial.
Es reversible. Archivar no borra movimientos, consumos, reposiciones ni usos.
[Eliminar un objeto](item-deletion.md) es independiente: borra definitivamente
el objeto y sus historiales, incluso archivado, una vez devueltas las piezas en uso.

## Objetos con historial

Desde el detalle: Archivar objeto → Confirmar archivado. El registro desaparece
del inventario habitual. Marca Mostrar objetos archivados para abrirlo, consultar
su historial o restaurarlo. Se conservan cantidad, tipo, modo individual/grupo,
visibilidad, ubicación e identificador. Archivar no registra un consumo ni pone
la cantidad a cero; tampoco representa un traslado físico.

Solo el propietario archiva/restaura. Un objeto en uso no se puede archivar: hay
que devolverlo antes. Mientras está archivado no admite edición, consumo, reposición,
movimiento ni retirada. Su propietario sí puede eliminarlo definitivamente. La lectura y el historial mantienen la privacidad anterior:
miembros leen compartidos, privados solo su propietario, sin excepción ADMIN global.
Si su ubicación está archivada, hay que restaurar primero la ubicación.

## Cajones o ubicaciones que dejan de usarse

Si el cajón solo cambia de nombre o de sitio, usar Editar y cambiar nombre/padre.
Si desaparece pero sus objetos siguen en uso, moverlos a su nuevo destino. Si los
objetos también salen del inventario habitual, archivarlos. Después, en Gestionar
ubicaciones, pulsar Archivar en el cajón. Tener historial ya no impide archivarlo.

No se permite archivar una ubicación con objetos activos (incluso registros con
stock0) o ubicaciones interiores activas. Los registros interiores ya archivados
pueden permanecer asociados para conservar sus referencias. No hay traslado en
bloque ni archivado recursivo automático.

Mostrar archivadas permite consultar/restaurar ubicaciones. Una archivada no se
ofrece como destino ni padre y no admite nuevos objetos o cambios de jerarquía.
Para restaurar una jerarquía, empezar por sus padres; después hijos y objetos.
Los miembros de la casa gestionan sus ubicaciones como ya podían editarlas.
Archivar una ubicación no concede acceso al inventario privado que la referencia.

## API y consistencia

- PATCH `/api/items/{id}/archive` y `/restore`: objeto actualizado.
- GET `/api/items?includeArchived=true`: incluir registros archivados además de activos.
- PATCH `/api/locations/{id}/archive` y `/restore`: ubicación actualizada.
- GET `/api/locations?houseId={id}&includeArchived=true`: incluir archivadas.

Los listados omiten archivados por defecto. GET por ID e historiales siguen
permitidos según acceso; los DTO incluyen archived. Los estados rechazados dan409,
los objetos compartidos ajenos no editables403 y recursos ajenos/inexistentes404.
Repetir archivo/restauración sin cambios intermedios es idempotente.

Las comprobaciones y cambios se serializan con el bloqueo de casa que ya usan
creación, movimientos, consumos y uso. No puede quedar un objeto activo en una
ubicación archivada por una carrera entre creación y archivo, ni archivarse una
pieza a la vez que se retira para usar. Restaurar no restaura implícitamente otros
registros. El recuento de la búsqueda incluye los registros mostrados cuando se
activa la visualización de archivados; no es un contador exclusivo de stock activo.

## Datos y comprobaciones

Columnas boolean archived notnull defaultfalse en items ylocations: registros
anteriores activos por defecto, sin modificar cantidades ni referencias históricas.
Copia PostgreSQL antes de actualizar. Migraciones versionadas y política de purga
siguen pendientes; no hay borrado automático de archivados.

149 tests Java y81 Angular, build y verificación Docker/PostgreSQL. Se prueban
permisos, conflictos, jerarquías, historia conservada, carreras archivo/creación y
archivo/uso, rollback forzado y persistencia durante3 recreaciones de contenedores.
Las pruebas visuales usan fixtures y nunca borran definitivamente datos del usuario.
