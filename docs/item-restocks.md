# Añadir unidades y consultar reposiciones

En el detalle, el propietario puede usar **Añadir unidades** sin abrir Editar.
Introduce un entero positivo con el campo o los botones −/+, revisa el total
previsto y pulsa Confirmar reposición. También funciona con consumibles agotados.
En reutilizables aumenta el total registrado y la disponibilidad, conservando
los objetos que siguen en uso. No cambia nombre, tipo, ubicación o privacidad.

Solo el propietario puede añadir: mantiene el permiso que ya tenía para editar
cantidades. Los miembros pueden ver reposiciones de objetos compartidos; objetos
privados e historiales siguen accesibles solo para su propietario. ADMIN global
y creador de la casa no evitan estas reglas. Cliente sin sesión recibe401;
objeto ajeno/inexistente404 y miembro lector que intenta reponer compartido403.

## API

- POST `/api/items/{id}/restocks`, JSON `{"quantity":5}`: devuelve200 con el objeto
  actualizado. La cantidad es el incremento, no el total deseado.
- GET `/api/items/{id}/restocks`: historial ordenado por fecha/ID descendente, con
  cantidad añadida, total tras reponer, usuario, fechaUTC conZ y ubicación.

Cada reposición se guarda en `item_restocks`. La actualización del total y el
registro son una sola transacción, con el bloqueo de casa que usa consumo/uso/
movimiento. Se suma al valor actual después del bloqueo, evitando perder cambios
concurrentes. La vista previa usa la última cantidad cargada: el resultado de la
API prevalece si otra sesión ha cambiado el stock.

Cantidades nulas, cero, negativas, decimales o fuera de Integer se rechazan400.
Si la suma supera Integer.MAX_VALUE, devuelve409 sin tocar cantidad ni historial.
La interfaz evita envíos repetidos mientras espera y cierra el formulario al
aceptarse. No reintenta automáticamente una escritura; no hay clave de idempotencia
para reintentos de red. Si una respuesta se pierde, consultar cantidad/historial
antes de volver a confirmar la misma reposición.

Un objeto con reposiciones no puede cambiar de tipo. Su propietario puede
eliminarlo junto con esas reposiciones mediante confirmación explícita. La ubicación
histórica queda protegida mientras existan referencias a ella.
Las etiquetas de ubicación reflejan la jerarquía actual, igual que otros historiales.
El ajuste manual de cantidad en edición permanece para corregir recuentos y no
se convierte retroactivamente en una reposición. No se genera historial ficticio.

## Datos y validación

Nueva tabla sin reescribir las filas existentes. La aplicación mantiene el esquema
mediante Hibernate update; migraciones versionadas siguen pendientes. Realizar
copia PostgreSQL antes de actualizar una base con datos importantes.

Pruebas Java: permisos, formato, agotados, límite entero, reutilizables en uso y
protección del historial. Angular: vista previa, delta, errores, botones, doble
confirmación y cambio de objeto. Docker/PostgreSQL comprueba sumas simultáneas,
consumo/reposición simultáneos, rollback forzado y persistencia durante3 ciclos.
