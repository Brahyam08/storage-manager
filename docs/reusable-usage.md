# Uso y devolución de reutilizables

Un reutilizable conserva `quantity` como total registrado. `inUseQuantity` suma las
retiradas activas y `availableQuantity = quantity - inUseQuantity`. Retirar dos de
cinco objetos deja total5, disponibles3, en uso2; devolverlos restaura disponibles5.
No se registra consumo ni movimiento al retirar o devolver.

- POST `/api/items/{id}/uses` con `{ "quantity": 1 }`: registra una retirada del
  usuario autenticado; solo reutilizables, cantidad entera positiva y disponible.
- GET `/api/items/{id}/uses`: historial accesible con actor ID, cantidad, fecha de
  retirada/devolución y ubicación habitual. No devuelve entidades de usuarios.
- POST `/api/items/{id}/uses/{useId}/return`: devuelve la retirada completa.
  Solo quien la retiró o el propietario puede hacerlo. Repetir devuelve409.

El acceso privado/compartido se aplica antes de consultar o cambiar usos. Un ADMIN
de aplicación no puede leer usos privados ajenos ni devolver retiradas compartidas
de terceros solo por su rol. No se admite enviar una identidad de actor distinta.

Retirada y devolución usan el bloqueo de casa que ya serializa inventario. No se
puede retirar por encima de lo disponible. Mientras hay objetos en uso no se puede
trasladar el conjunto, cambiar visibilidad/tipo ni bajar el total por debajo de lo
retirado. Un historial de uso impide cambiar de tipo. Tras devolver las piezas, su propietario
puede eliminar definitivamente el objeto y sus historiales. Las ubicaciones referenciadas por ese historial no se borran.

Cada devolución registra todos los objetos de esa retirada en su ubicación habitual.
No hay devolución parcial, destino alternativo, fechas límite ni identificación
individual de ejemplares. Tras devolver se puede usar la acción de movimiento.

Si solo queda un objeto disponible, Angular muestra directamente Retirar para usar
y envía cantidad1. El campo de cantidad aparece cuando hay varios disponibles.

Angular refresca el objeto tras una acción aceptada y carga su historial. Si falla
ese refresco después de aceptar la acción, bloquea repetirla y pide actualizar;
no confunde un fallo de lectura con una retirada fallida. Cambiar selección cancela
peticiones, sin prometer deshacer una operación ya aceptada por el servidor.

## Verificación

122 pruebas Java y 48 Angular pasan, además de build de producción. Se cubren
privacidad, suplantación del actor, disponibilidad, permisos de devolución, historial,
validación, cambios incompatibles y fallo parcial de actualización de la interfaz.
El verificador Docker comprueba retiradas concurrentes (solo una para un objeto),
rollback de devolución ante fallo SQL, doble devolución y persistencia tras tres
recreaciones. Navegador con PostgreSQL aislado: ratón1 disponible→en uso→devuelto,
traslado bloqueado durante uso y habilitado tras devolución; móvil sin overflow.

La tabla `item_uses` es nueva; los objetos existentes no tienen retiradas activas y
conservan sus totales. Se mantiene la actualización de esquema de desarrollo; las
migraciones versionadas para producción siguen pendientes. Respaldar la base antes
de actualizar. Sin cambios en cifrado ni en el almacenamiento acordado para la demo.
