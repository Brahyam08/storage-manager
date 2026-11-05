# Preparación de una beta compartida

El frontend se entrega al navegador y puede inspeccionarse. Minificar, ofuscar o
codificar en Base64 no impide llamar a la API ni protege secretos. Las claves,
contraseñas de PostgreSQL y el secreto JWT pertenecen exclusivamente al servidor.
HTTPS cifra el tráfico; las reglas de autorización deciden qué datos puede leer o
cambiar cada cuenta. Ninguna de estas medidas sustituye a las otras.

## Perfil beta cerrado

`SPRING_PROFILES_ACTIVE=beta` activa `application-beta.yaml` al arrancar la API.
En Docker Compose puede indicarse en el archivo de entorno de la instancia.

- Rechaza el registro en el servidor con 403, incluso llamando directamente a la
  API. Las cuentas existentes siguen pudiendo iniciar sesión.
- Desactiva Swagger y la descripción OpenAPI. Esto reduce exposición accidental
  de documentación, pero no se considera un control de acceso al inventario.
- Desactiva el log SQL y la inclusión de mensajes internos, excepciones, trazas y
  errores de validación en las páginas de error de Spring Boot.
- Mantiene desactivada la confianza en cabeceras reenviadas por clientes.
- Aplica CSP: scripts, estilos, conexiones y manifiesto solo del mismo origen;
  imágenes locales o `data:`; bloquea objetos, workers, bases e incrustación en iframes.
  No permite scripts inline ni eval. La compilación no inserta CSS crítico ni
  controladores inline para cargarlo.
- Envía Referrer-Policy no-referrer, restringe cámara/micrófono/geolocalización y
  añade HSTS sin includeSubDomains para la conexión HTTPS terminada por el túnel.
  Los navegadores ignoran HSTS si la vista previa se abre por HTTP local.

El perfil local normal conserva su registro. El perfil beta no cifra campos de la
base, no crea una base separada y no habilita un túnel o acceso público por sí solo.

## Separación antes de abrir el enlace

Usar un proyecto Compose nuevo (`storage-beta`), un archivo de entorno privado
fuera de Git, contraseñas y secreto JWT nuevos y su propio volumen PostgreSQL. No
restaurar una copia del inventario personal en esa base. Mantener los puertos de
app y base en `127.0.0.1`; el túnel solo debe apuntar al puerto web de la beta.

Crear cuentas ficticias de testers con el proyecto aislado en perfil local y
registro abierto, todavía sin túnel y accesible solo desde el Mac. Después cambiar
ese archivo de entorno a `SPRING_PROFILES_ACTIVE=beta`, recrear solo la API y
comprobar que un registro directo devuelve 403 antes de habilitar acceso externo.
No habilitar de nuevo el registro mientras el túnel esté publicado. El correo de
registro no se verifica: una lista de correos permitidos por sí sola no demostraría
que quien se registra controla ese buzón.

## Pendiente antes de publicar

- Preparar y comprobar realmente el entorno separado y las cuentas de testers.
- Configurar HTTPS y probarlo desde una red externa y móviles reales.
- CSP aplicada y comprobada con build empaquetado: acceso, detalle, selector y
  descarga CSV sin violaciones registradas. Repetir bajo HTTPS en iPhone.
  El token persistente sigue accesible a JavaScript del mismo origen; un XSS podría
  robarlo. Este perfil no lo convierte en una cookie HttpOnly ni en cifrado E2E.
- Validar límites de peticiones detrás del túnel. Con la configuración actual,
  los clientes pueden compartir la cuota de la dirección del proxy. No confiar
  ciegamente en X-Forwarded-For para separarlos.
- Repetir pruebas de permisos entre testers, objetos privados, miembros y ajenos;
  probar copia y recuperación del entorno beta sin tocar datos personales.

Esta entrega prepara el cierre del registro y la configuración de errores. No es
una auditoría integral ni significa que la beta ya esté publicada o lista para
almacenar información sensible real.

Referencias: [autorización OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
y [cabeceras de Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html).

La guía del Mac y activación controlada está en [HTTPS con Funnel](https-funnel.md).
