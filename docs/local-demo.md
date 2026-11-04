# Demo local para una entrevista

La demo se ejecuta en el ordenador con Docker. No necesita contratar alojamiento.
La web y la API están en http://localhost:8080. Los datos persisten en PostgreSQL.
Las capturas del repositorio usan datos ficticios, no una cuenta compartida.

## Preparación

En una copia nueva, configura `.env` desde `.env.example` y arranca:

```bash
docker compose up -d --build
```

Si ya tienes un proyecto Compose con datos, usa su mismo nombre y archivo de
entorno al arrancarlo; cambiar de proyecto crea otro volumen y puede parecer que
la base está vacía. No elimines volúmenes para preparar la presentación.

Crea dos cuentas de prueba en dos pestañas. La sesión se mantiene en memoria por
pestaña: refrescar exige iniciar sesión otra vez, pero conserva el inventario.
No hay contraseña ni usuario de demostración incluidos en el repositorio.

Con la primera cuenta crea una casa, una ubicación Estudio y un Cajón dentro.
Añade estos ejemplos en el cajón:

| Objeto | Tipo | Cantidad | Visibilidad |
| --- | --- | --- | --- |
| Tornillos M3 | Consumible | 10 | Compartido |
| Ratón de repuesto | Reutilizable | 1 | Privado |
| Arandelas M4 | Consumible | 0 | Compartido |

Genera una invitación y une la segunda cuenta a la casa. Prepara otra ubicación
para el traslado. Si ensayas varias veces, usa objetos nuevos o registra las
cantidades resultantes; la demo modifica los datos de prueba y conserva historial.

## Recorrido de cinco minutos

| Tiempo | Qué mostrar | Qué explicar |
| --- | --- | --- |
| 0:00–0:40 | Casa y ruta Estudio → Cajón | El problema es encontrar objetos físicos y saber dónde se guardan. |
| 0:40–1:20 | Buscar Tornillos y filtrar agotados | Un consumible conserva su registro incluso con cantidad cero. |
| 1:20–2:00 | Gastar 5 de 10 y abrir consumos | Quedan 5; se guarda actor, cantidad, fecha y ubicación sin mover el objeto. |
| 2:00–2:50 | Retirar y devolver el ratón | El total sigue siendo 1; cambian disponibles/en uso. No se gasta un reutilizable. |
| 2:50–3:30 | Mover los tornillos y ver movimientos | Se traslada la cantidad completa dentro de la casa y se registra origen/destino. |
| 3:30–4:15 | Segunda pestaña como otro miembro | Ve lo compartido, pero no el ratón privado de la primera cuenta. |
| 4:15–5:00 | Tests y decisiones técnicas | Backend autoritativo, transacciones, límites conocidos y siguiente mejora. |

Puedes enseñar una cancelación de borrado para explicar la confirmación y la
protección del historial. Usa solo registros que puedas eliminar si decides
confirmar. Las ubicaciones de la casa son compartidas; sus nombres no son secretos.

## Cómo explicar el código

No memorices una lista de tecnologías. Sigue una petición concreta:

1. Formulario Angular valida una cantidad y llama al servicio HTTP.
2. El interceptor añade Bearer a `/api/`; el servidor valida el JWT.
3. El servicio Java comprueba acceso y ejecuta la operación en una transacción.
4. PostgreSQL guarda el cambio y su historial. La respuesta actualiza la interfaz.

Preguntas para practicar con tus propias palabras:

- ¿Por qué el guard Angular no sustituye los permisos de la API?
- ¿Por qué mover y consumir son operaciones distintas de editar detalles?
- ¿Cómo evitas que dos retiradas gasten la misma disponibilidad? ¿Qué coste tiene
  bloquear las operaciones de una casa mientras se actualiza su inventario?
- ¿Qué pasa si falla guardar el historial o si se repite una devolución?
- ¿Qué ocurre si el servidor acepta una retirada pero falla la lectura posterior?
- ¿Quién puede leer un objeto privado y quién puede leer directamente la base?
- ¿Por qué la API envía las fechas con zona UTC?
- ¿Qué mejorarías con muchos objetos: paginación y consultas agregadas de usos?

## Límites que conviene explicar

La demo actual usa Angular, Java y PostgreSQL locales. El inventario no tiene
cifrado de campos frente a administradores de la base o copias legibles. La app
instalable con almacenamiento local cifrado, sincronización, cambios de identidad
visual y ejercicios de mantenimiento se han pospuesto. No se han introducido fallos
intencionales en la rama principal. Revisa [privacidad](security-and-storage.md),
[uso/devolución](reusable-usage.md) y [fechas](history-timestamps.md).

Para reproducir las comprobaciones en tu ordenador, usa `./scripts/verify_local.sh`.
No hace push, no publica imágenes y no usa tu base para los tests de Docker.


## Capturas

[Escritorio](images/inventory-desktop.jpg) · [Móvil](images/inventory-mobile.jpg). Ambas capturas usan cuentas e inventario ficticios.
