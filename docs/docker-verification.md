# Verificación de Docker y PostgreSQL 16

## Arranque de desarrollo

Con Docker Desktop iniciado, copia .env.example a .env solo si aún no lo tienes,
sustituye los placeholders y ejecuta `docker compose up --build`.
La API escucha en http://localhost:8080 y PostgreSQL en localhost:5433 por defecto;
API_PORT y DB_PORT permiten elegir otros puertos. Ambos se publican solo en loopback.
La API espera al healthcheck de PostgreSQL. No hay nombres fijos de contenedores,
por lo que se pueden ejecutar proyectos Compose separados sin colisiones de nombres.

Los datos se guardan en el volumen Compose storage-data. Un reinicio o
`docker compose down` conserva ese volumen. No añadas `--volumes` al detener tu
entorno habitual si quieres conservar sus datos. .env y las notas locales están
fuera de Git y del contexto de construcción mediante .dockerignore. El contenedor
de la API deshabilita los logs SQL.

## Verificación reproducible y aislada

Requisitos: Python 3, Docker con Compose, Docker Desktop en ejecución y acceso para
descargar las imágenes y dependencias Maven si aún no están en caché.

```bash
python3 scripts/verify_docker.py
```

El script utiliza solo la biblioteca estándar de Python. Genera un nombre de
proyecto storage-verify-* y credenciales aleatorias propias en un archivo temporal
con permisos 0600. Usa puertos aleatorios y un volumen nuevo. No lee .env del
usuario ni reutiliza su base de datos; los valores exportados de PostgreSQL/JWT
se sustituyen únicamente en el proceso de verificación.

Comprueba mediante HTTP contra la API construida con el Dockerfile:

1. Actualización automática de un esquema antiguo simulado: users, locations,
   items e item_movements, con cuenta, ubicaciones, objeto e historial ficticios.
   Comprueba que se conservan y que el inventario antiguo no se asigna ni expone.
2. Registro/login, creación de casa, invitación, membresía y ubicaciones.
3. Objetos privados y compartidos, búsqueda, consumo, actor de sesión, cantidades
   inválidas, stock cero e historial protegido frente a otras cuentas.
4. Consumos concurrentes que no gastan dos veces las mismas unidades.
5. Movimientos concurrentes que conservan una cadena de historial consistente.
6. Rollback de consumo y movimiento cuando un trigger temporal de la base de
   prueba fuerza el fallo al actualizar el objeto. Se retira después de la prueba.
7. Tres ciclos de eliminación y recreación de los contenedores conservando el volumen:
   login, membresía, existencias e historiales deben sobrevivir sin cambios.

Al finalizar, incluso ante errores, ejecuta down --volumes solo sobre su proyecto
aleatorio. Elimina sus contenedores, red y volumen; no toca los del entorno habitual.
Las capas/imágenes de construcción pueden quedar en la caché de Docker.

## Resultado y límites — 3 de noviembre de 2026

La última verificación pasó con PostgreSQL 16 en Docker Desktop, incluyendo
concurrencia, rollback y tres ciclos consecutivos de recreación de contenedores. No se conectó a la base PostgreSQL real del
usuario ni se modificaron sus registros. La suite Java previa conserva 75 tests H2
aprobados; este script comprueba además el sistema real por HTTP, no sustituye esa
suite ni ejecuta todas sus combinaciones de permisos.

La aplicación aún usa Hibernate ddl-auto=update. La prueba demuestra que la
actualización del esquema simulado conserva sus datos; no es una migración
versionada ni garantiza actualizar cualquier base existente. Antes de actualizar
una base con datos reales hace falta copia de seguridad y revisión de ese esquema.
Quedan pendientes migraciones controladas para producción, validación de registro/
login, errores generales, análisis de dependencias y el frontend. No se ha validado
Swagger ni un despliegue remoto y todavía no se considera lista una demo pública.

Una ejecución intermedia agotó la espera de arranque de la API tras recrear los
contenedores. No se capturaron logs de esa primera incidencia y no se identificó
su causa. Se añadieron diagnóstico de estado/logs (con secretos ocultos), dirección
de la API y último error de disponibilidad. La siguiente pasada y los tres ciclos
consecutivos finales no reprodujeron el fallo. Se conserva como incidencia pendiente
de reproducir; no se afirma que su causa esté corregida.
