# Edición de ubicaciones y borrado

En el inventario, **Gestionar ubicaciones** permite listar, renombrar, describir y
reorganizar las ubicaciones de la casa seleccionada. Los miembros de la casa
pueden gestionarlas según el contrato existente de la API. Al guardar se recargan
las rutas de las ubicaciones y del inventario, incluidos sus descendientes.

Los destinos de jerarquía excluyen la propia ubicación y sus descendientes. La API
vuelve a validar casa, existencia y ausencia de ciclos; la lista del navegador no
es una autorización. Las ubicaciones continúan siendo compartidas por la casa.

El propietario de un objeto puede abrir **Eliminar objeto**. La API conserva los
objetos con historial de movimientos, consumos o usos y devuelve409 al intentar
eliminarlos. El borrado disponible es permanente; esta entrega no añade archivo,
papelera ni borrado del historial. No se deben borrar datos para ocultar conflictos.

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
objetos y sububicaciones; no es necesario borrarlo. Si el cajón desaparece, hoy
se puede mover cada objeto a otro destino. El borrado sigue bloqueado si quedan
sububicaciones o referencias históricas, aunque ya no haya objetos.

Trasladar todo el contenido y archivar la ubicación es una mejora pendiente;
no está implementada y no debe confundirse con el borrado actual.

## Comprobaciones

47 tests Angular y build de producción pasan. Las pruebas cubren confirmación y
cancelación, propiedad, conflicto de historial, destinos jerárquicos permitidos,
actualización de rutas y cambio de contexto durante peticiones. El backend no ha
cambiado; su última suite verificada contiene116 tests.

En PostgreSQL aislado se comprobaron borrado de objeto sin historial y ubicación
vacía (204 y posterior404), rechazo de ubicación ocupada, objeto con consumo y
ciclo jerárquico. En navegador se verificaron confirmación/cancelación sin borrado,
renombrado propagado a objetos y cambio de ubicación padre desde móvil; sin
desbordamiento horizontal ni errores de consola. Los borrados reales de fixtures
se comprobaron por API; las confirmaciones visuales se cancelaron.

Los servicios y datos del usuario se conservan. Esta entrega no modifica cifrado,
almacenamiento, permisos del servidor ni los límites de privacidad documentados.
