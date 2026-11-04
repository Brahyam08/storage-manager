# Verificación local y GitHub opcional

La ruta principal de este proyecto es local. Con Java 21, Node 24, Python 3 y Docker
con Compose disponibles, desde la raíz ejecuta:

```bash
./scripts/verify_local.sh
```

El script detiene la secuencia si falla un comando:

1. Maven Wrapper: tests H2 y empaquetado Java (`verify`), aislando las variables
   `SPRING_*` y opciones de JVM/Maven exportadas para no heredar otra base.
2. `npm ci`, tests Angular y compilación de producción con el lockfile.
3. Verificador Docker/PostgreSQL en un proyecto aleatorio descartable.

Los tests de Docker generan sus propios secretos y datos ficticios, y retiran sus
contenedores y volumen al terminar. No leen `.env` ni se conectan al playground.
No hay actualización de dependencias, publicación de imágenes ni push. El comando
reinstala `frontend/node_modules`; ejecutarlo sin el servidor de desarrollo evita
interferir con sus watchers. La aplicación Docker en 8080 es independiente de ellos. El aislamiento de variables
se aplica en un subproceso; no cambia la configuración de tu terminal.

## Workflow remoto opcional

`.github/workflows/verify.yml` conserva los mismos tres grupos de comprobaciones
en jobs separados, con acciones oficiales fijadas por SHA. Solo tiene el evento
manual `workflow_dispatch`: hacer push o abrir una PR no lo ejecuta automáticamente.
No se ha lanzado ninguna ejecución remota desde esta entrega. Los límites/condiciones
de una ejecución alojada dependen de GitHub; para mantener las comprobaciones en tu
ordenador usa el script local.

El workflow tiene permisos `contents: read`, no persiste credenciales de checkout,
no consume secretos de despliegue y no publica artefactos en un registry. Usa
Ubuntu 24.04, Java 21 y Node 24. El runner Docker usa las herramientas preinstaladas
[documentadas por GitHub](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md).

Acciones consultadas el 4 de noviembre de 2026:

- [actions/checkout v7.0.1](https://github.com/actions/checkout/tree/3d3c42e5aac5ba805825da76410c181273ba90b1).
- [actions/setup-java v6.1.0](https://github.com/actions/setup-java/tree/b843ebf9b913146b38da2eaa7aeddf76074ae1ad).
- [actions/setup-node v7.1.0](https://github.com/actions/setup-node/tree/949feb2413d6458794dcd2491c4babbbce0c15c1).

## Alcance comprobado

El script completo se ha ejecutado con variables de Spring y JVM que apuntaban
a una base inválida de prueba: fueron aisladas y la secuencia terminó correctamente.
Los comandos Java y Angular del workflow se han ejecutado localmente; el proyecto
contiene 153 tests Java y 83 Angular. La configuración del workflow pasa actionlint
1.7.12 y el script pasa `bash -n`. Docker ejecuta el mismo verificador existente,
con rutas, privacidad, concurrencia, rollback y tres ciclos de persistencia.
Una ejecución futura en GitHub será la prueba del entorno remoto: la validación
local no se presenta como un check verde ya obtenido allí.

La [lista de revisión de la beta](beta-checklist.md) separa las comprobaciones
automáticas de los recorridos manuales y las pruebas pendientes en móviles reales.
