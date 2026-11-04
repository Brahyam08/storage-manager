# Edición de ubicaciones y borrado

En el inventario, **Gestionar ubicaciones** permite listar, renombrar, describir y
reorganizar las ubicaciones de la casa seleccionada. Los miembros de la casa
pueden gestionarlas según el contrato existente de la API. Al guardar se recargan
las rutas de las ubicaciones y del inventario, incluidos sus descendientes.

Los destinos de jerarquía excluyen la propia ubicación y sus descendientes. La API
vuelve a validar casa, existencia y ausencia de ciclos; la lista del navegador no
es una autorización. Las ubicaciones continúan siendo compartidas por la casa.

El propietario puede **Eliminar objeto** aunque tenga historial o esté archivado.
La confirmación advierte que se borran también movimientos, consumos, reposiciones
y usos anteriores. Si hay piezas en uso, se deben devolver antes. Es irreversible
desde la aplicación; [reglas de eliminación](item-deletion.md). Para conservar los
registros, usar el [archivado reversible](archiving.md).

Una ubicación solo puede eliminarse si no contiene otras ubicaciones, objetos ni
referencias desde los historiales. La confirmación explica esa condición. Cancelar
no envía ninguna petición DELETE. Un conflicto mantiene el registro visible y
muestra la respuesta de la API. Las confirmaciones reciben foco para teclado.

Al cambiar de casa o de objeto se cancelan las peticiones pendientes y se cierran
las confirmaciones. Cancelar una petición no revierte una operación ya aceptada
por el servidor. Después de una edición aceptada se cierra el editor antes de
recargar la lista: un fallo de lectura no permite repetir el guardado por accidente.
Las selecciones de ubicaciones que ya no existen se vacían en los formularios.

## Reorganizar un cajón con contenido

Si sigue siendo el mismo cajón, editar su nombre o ubicación padre conserva sus
objetos y sububicaciones; no es necesario borrarlo. Si el cajón desaparece, usa
**Trasladar objetos** para llevar sus objetos activos directos a otro destino y
archiva después el cajón. El traslado no incluye sububicaciones ni archivados;
[contrato y permisos](location-contents.md). El borrado sigue bloqueado si quedan
sububicaciones o referencias históricas, aunque ya no haya objetos.

Archivar/restaurar permite conservar los historiales. Ninguna operación mueve ni
archiva contenido en cascada automáticamente.

## Comprobaciones

Las suites Java y Angular cubren confirmación y cancelación, propiedad, privacidad,
destinos jerárquicos permitidos, actualización de rutas y cambios de contexto.
El verificador PostgreSQL aislado comprueba restricciones de borrado de ubicaciones,
traslados atómicos, concurrencia y persistencia. Los objetos se pueden eliminar con
historial según las [reglas actuales](item-deletion.md).

Los servicios y datos del usuario se conservan. Esta entrega no modifica cifrado,
almacenamiento, permisos del servidor ni los límites de privacidad documentados.
