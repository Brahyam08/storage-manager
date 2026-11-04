# Fechas de los historiales

Las operaciones nuevas registran UTC de forma explícita, también si la JVM se
arranca en una zona distinta. Los DTO de movimientos, consumos y usos/devueltas
incluyen el desplazamiento UTC en ISO8601 (`Z`). Angular interpreta el instante y
lo muestra en la zona local del navegador.

Antes se enviaba LocalDateTime sin zona. Los contenedores registraban UTC y el
navegador lo interpretaba como hora local: en Madrid aparecía una hora menos en
noviembre. Añadir la zona corrige la presentación de esos registros existentes.
No se han reescrito fechas, cantidades ni historiales en PostgreSQL.

La columna actual sigue guardando fecha/hora sin zona bajo el contrato UTC.
Para registros históricos importados de procesos antiguos con otra zona hay que
identificar primero la zona de origen. No se debe asumir UTC ni aplicar un cambio
masivo a esos datos. Esta corrección se ha verificado sobre el entorno Docker UTC;
las migraciones versionadas y una eventual conversión a un tipo temporal con zona
son trabajo posterior.

Las pruebas HTTP de las cuatro fechas comprueban que se pueden interpretar como
instantes y que corresponden al momento de la operación. El verificador Docker
comprueba los sufijos UTC y la persistencia de las respuestas tras recreaciones.
