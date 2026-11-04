# Web y API en una sola imagen

El Dockerfile compila Angular con Node24 y npmci a partir del lockfile. Después
copia el resultado de producción y sus licencias a los recursos estáticos del JAR.
La imagen final contiene Java21 y el JAR; no necesita Node ni un servidor Angular.
El proceso de la aplicación se ejecuta con UID10001, sin root.

## Arranque local

Configura `.env` desde `.env.example` con tus valores locales y arranca:

```bash
docker compose up -d --build
```

Abre http://localhost:8080. La web y `/api/**` comparten origen; no hay que configurar
CORS ni el proxy de desarrollo para esta modalidad. Swagger permanece disponible
en http://localhost:8080/swagger-ui/index.html. `API_PORT` permite cambiar el puerto.

El volumen PostgreSQL conserva las cuentas y el inventario. `docker compose down`
no elimina ese volumen. No uses `--volumes` con datos que quieras conservar.
Actualizar la imagen no requiere cambiar contraseñas ni crear una nueva base.
Realiza una copia antes de actualizar un entorno con datos importantes.

## Rutas y acceso

GET/HEAD de `/`, `/acceso`, `/registro` e `/inventario` reciben la entrada Angular.
El guard del cliente sigue llevando a acceso cuando no hay sesión; servir el HTML
no entrega inventario. Los endpoints protegidos de la API mantienen la autenticación
y las reglas de propiedad/membresía. Solo los recursos públicos del frontend tienen
acceso anónimo; un archivo o API desconocido no se sustituye por index.html.

Los recursos siguen atravesando Spring Security y sus cabeceras. Los despachos
internos FORWARD/ERROR se permiten para servir la vista fija y las respuestas de
error, siguiendo la [configuración documentada de Spring Security](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html).
No hay un forward calculado a partir de parámetros del usuario.

Refrescar restaura el token de sessionStorage y verifica el perfil con la API.
Cerrar sesión o recibir un rechazo por caducidad elimina ese token. El inventario
permanece en PostgreSQL. Los bundles se generan sin mapas de
fuente de producción y las licencias se distribuyen con la aplicación.

## Desarrollo y comprobaciones

Para modificar la interfaz con recarga automática, usa `npm start` en `frontend/`
y http://localhost:4200 con la API en8080. `mvn spring-boot:run` arranca la API para
desarrollo; no compila Angular. La distribución conjunta la construye Docker.

`python3 scripts/verify_docker.py` crea un proyecto descartable, comprueba Angular,
rutas directas, GET/HEAD, JS/CSS/favicon/licencias, aislamiento API, UID y persistencia
tras tres recreaciones, además de las regresiones de PostgreSQL existentes.
La prueba usa datos ficticios y no lee el `.env` del usuario.

Esta entrega prepara la distribución local. El alojamiento público, HTTPS, política
CSP, roles mínimos de base de datos, revisión de imágenes y migraciones versionadas
siguen pendientes de la preparación del despliegue. No se ha publicado nada.
