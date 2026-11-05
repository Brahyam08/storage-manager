# Copias locales y prueba de recuperación

Requiere Python3.10 o posterior (solo biblioteca estándar) y Docker con Compose.
Desde la raíz del repositorio, con Docker abierto y el servicio `db` del proyecto
ya iniciado:

```bash
python3 scripts/local_backups.py backup \
  --project storage-playground \
  --env-file .env.playground
```

El comando imprime la ruta del archivo creado. Por defecto usa `../backups`, fuera
del repositorio. Para otra instalación, indica su proyecto Compose y archivo de
entorno reales: no deduce el proyecto ni inicia/recrea servicios. Variables de
conexión exportadas en la terminal no sustituyen el archivo de entorno indicado.

La carpeta es privada (`700`) y cada copia tiene permisos `600`. Puedes elegir
otra carpeta privada con `--output-dir /ruta/privada/backups`. El script rechaza
carpetas dentro del repositorio o enlaces simbólicos y nunca sobrescribe una copia.
Los archivos parciales se retiran si falla el proceso. `.gitignore` y `.dockerignore`
excluyen también dumps y carpetas de copias como defensa adicional.

## Comprobar una copia

Usa exactamente la ruta que imprimió el comando anterior:

```bash
python3 scripts/local_backups.py verify /ruta/privada/backups/archivo.dump
```

La comprobación crea un PostgreSQL16 temporal con contraseña aleatoria, sin red,
sin puertos publicados y sin montar volúmenes o carpetas del usuario. Restaura la
copia con errores estrictos, comprueba las nueve tablas y sus relaciones, y retira
ese contenedor al terminar, también si falla la restauración. Los datos temporales
se guardan en memoria; el contenedor está limitado a1GiB. Copias grandes pueden
superar ese límite y requerir un procedimiento de recuperación específico.

No sustituye la base en uso ni cambia la aplicación a la copia recuperada. Esta
primera entrega automatiza la copia y la **prueba de recuperación aislada**; no
ofrece restauración destructiva sobre la base real. Si Docker deja de responder
al limpiar, el error identifica el contenedor temporal que debe retirarse después.

Una copia se crea con `pg_dump` custom, que usa una instantánea consistente aunque
la web siga registrando cambios. La validación del índice al crearla no basta para
confirmar que todos los datos se restauran: ejecuta también `verify`.

## Qué contiene

Incluye todas las tablas y datos de esa base, todas las cuentas (con sus hashes
de contraseña), objetos privados/compartidos, ubicaciones, historiales y secuencias.
No filtra por usuario y no cifra el archivo. Solo el operador local debe acceder a
él. No lo subas al repositorio ni lo compartas como si fuera una exportación pública.

No incluye archivos `.env`, la clave JWT, roles/permisos del clúster PostgreSQL ni
imágenes de objetos. La restauración temporal usa su propio propietario y omite
privilegios del origen. El script no decide cuándo borrar las copias anteriores.

## Verificación del desarrollo

```bash
python3 -m unittest discover -s scripts -p 'test_local_backups.py'
python3 scripts/verify_docker.py
```

Las pruebas de archivos cubren permisos, destino privado, sobrescritura rechazada,
archivo incorrecto, enlaces, errores y limpieza. El verificador Docker hace una
copia de datos ficticios, restaura las nueve tablas y compara todo su contenido;
también comprueba secuencias, aislamiento, archivo truncado y origen sin cambios.
No ejecuta esas pruebas sobre tu base real. `scripts/verify_local.sh` incluye ambas.
