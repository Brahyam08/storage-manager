# Trasladar el contenido de una ubicación

En **Gestionar ubicaciones → Trasladar objetos**, revisa el resumen, elige un
destino y confirma. Se trasladan todos los objetos activos guardados directamente
en esa ubicación, con su cantidad completa, incluidos registros con cero unidades.
Las sububicaciones y los objetos archivados permanecen en su sitio.

Es una única transacción: cada objeto registra su movimiento y conserva los
historiales anteriores. Si falla cualquier paso no se traslada ninguno. El destino
debe ser otra ubicación activa de la misma casa. No se elimina ni archiva el origen;
puede archivarse después si no quedan objetos ni sububicaciones activos. [Eliminar la ubicación](location-deletion.md) permite dejar los objetos restantes
sin ubicación, conservando los historiales.

## Permisos y confirmación

Los miembros pueden trasladar los objetos compartidos y sus propios privados.
Si hay objetos activos privados de otra persona, la operación conjunta se rechaza
con un mensaje genérico sin nombres ni cantidades. Deben reorganizarlos sus dueños;
no se traslada silenciosamente un subconjunto. Tampoco se permite si hay piezas
en uso: deben devolverse antes.

La API vuelve a comprobar los permisos, el conjunto de identificadores y sus
cantidades bajo el bloqueo de la casa. Si se añadieron, retiraron o cambiaron las
cantidades desde el resumen, responde `409` y exige actualizarlo. No es una versión
completa de todos los campos: cambios de nombre o descripción no invalidan el resumen.
La interfaz no repite automáticamente una escritura fallida. Si se perdió la
respuesta, actualizar el resumen permite conocer el contenido actual.

## API

- `GET /api/locations/{id}/contents`: lista de objetos activos accesibles; `409`
  si no se puede trasladar el conjunto completo por permisos o piezas en uso.
- `POST /api/locations/{id}/move-contents`:
  `{"toLocationId":2,"items":[{"id":7,"quantity":10},{"id":8,"quantity":0}]}`.
  Devuelve los objetos trasladados. Lista de 1 a 500 registros (no unidades), sin
  identificadores repetidos. Destino inválido o lista mal formada: `400`;
  contexto no accesible: `404`; resumen desactualizado o ubicación archivada: `409`.

La previsualización está limitada a 500 registros. Para más registros hay que
reorganizar por partes mediante los movimientos individuales.

## Verificación

Pruebas Java: objetos compartidos, privacidad, destinatarios, registros agotados,
archivados y sububicaciones excluidos, piezas en uso y resúmenes desactualizados.
Pruebas Angular: resumen, cuerpo exacto, doble pulsación, error con actualización
explícita y cancelación al cambiar de casa. El verificador Docker usa PostgreSQL
con datos sintéticos: fallo de actualización del segundo objeto con rollback de
ambos movimientos, confirmaciones concurrentes y persistencia tras recrear contenedores.
