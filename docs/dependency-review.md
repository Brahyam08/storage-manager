# Revisión de dependencias y Swagger — 3 de noviembre de 2026

Se revisó el grafo Maven resuelto de ejecución y las ramas compatibles antes de
seguir con la interfaz. No se cambió a una nueva rama funcional de Spring Boot.

| Componente | Antes | Después | Motivo |
|---|---|---|---|
| Spring Boot parent | 4.0.6 | 4.0.9 | Parche de la rama 4.0 y su conjunto de dependencias gestionadas |
| springdoc WebMVC UI | 2.8.0 | 3.0.3 | Rama 3.0 compatible con Boot 4.0 |
| JJWT (tres módulos) | 0.12.6 | 0.12.7 | Parche de la rama utilizada, con versión común en el POM |

Las versiones transitivas se mantienen bajo el BOM de Boot, sin sobrescribirlas
individualmente para silenciar avisos. La matriz oficial de springdoc sitúa 3.0.x
con Boot 4.0.x y 3.1/3.2 con Boot 4.1. Por eso no se eligió simplemente la mayor
versión de springdoc ni se saltó a Boot 4.1 para esta entrega.

## Consulta OSV reproducible

```bash
mvn dependency:tree -Dscope=runtime -DoutputType=json -DoutputFile=target/dependencies.json
python3 scripts/check_dependencies.py target/dependencies.json
```

El script usa la API pública OSV y envía nombres y versiones de las dependencias
del árbol, no código, credenciales o datos de la aplicación. En este proyecto son
coordenadas de librerías públicas; revisar el inventario si se añaden dependencias
privadas. Excluye el nodo raíz del proyecto, deduplica coordenadas y sigue las
páginas de resultados. Un fallo de red/respuesta no se presenta como revisión limpia.
Guarda fecha, alcance y avisos en target/dependency-audit.json; termina con código 1
si hay avisos y 0 si no los hay. target permanece excluido de Git.

Resultado del 3 de noviembre de 2026:

- Antes: 98 coordenadas resueltas; OSV devolvió avisos para 15 dependencias.
- Después: 99 coordenadas resueltas; OSV no devolvió avisos.

La presencia de un aviso por versión no demuestra que el camino vulnerable se
utilice en esta aplicación. No se clasificó la explotabilidad de cada aviso antiguo;
se actualizó el conjunto gestionado y se verificaron sus regresiones. Un resultado
sin avisos tampoco demuestra ausencia de vulnerabilidades: depende del catálogo y
la fecha. Esta consulta no cubre dependencias de test/plugins, JDK, sistema operativo,
capas Docker, frontend ni un análisis del código de la aplicación.

## Swagger y pruebas

La versión anterior servía la UI y el esquema en el test, pero no documentaba
Bearer. No se afirma que Swagger estuviera completamente roto: se corrigió la
rama de compatibilidad y se añadió configuración OpenAPI explícita. Ahora el esquema
incluye Bearer JWT y las rutas de inventario; registro/login declaran seguridad
vacía porque son públicos. La documentación no sustituye la autorización real.

Dos pruebas verifican esquema, seguridad y recursos de Swagger sin sesión. Docker
comprueba además /v3/api-docs y /swagger-ui/index.html con la aplicación empaquetada.
La suite completa pasa 101 tests. También pasa el verificador PostgreSQL/Docker:
privacidad, consumos/movimientos concurrentes, rollback, cuotas y tres ciclos de
persistencia. No se verificó un navegador interactuando con Authorize; sí el esquema
y los recursos HTTP que utiliza.

El agente Mockito para Java 21 se resuelve mediante Maven Dependency Plugin y se
carga en Surefire con su misma versión resuelta. `mvn test` funciona sin rutas
absolutas del equipo ni -DargLine manual. No se actualiza producción con ese agente.

## Fuentes primarias consultadas

- [Versiones gestionadas por Boot 4.0](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html).
- [Matriz oficial springdoc/Boot](https://springdoc.org/).
- [JJWT 0.12.7](https://github.com/jwtk/jjwt/releases/tag/0.12.7).
- [Mockito: instrumentación Java 21](https://github.com/mockito/mockito/blob/main/mockito-core/src/main/java/org/mockito/Mockito.java).
- [API OSV querybatch](https://google.github.io/osv.dev/post-v1-querybatch/).

Pendientes: migraciones versionadas para producción, revisión de imágenes/JDK y
despliegue, y frontend Angular. No se presenta el resultado como auditoría integral.
