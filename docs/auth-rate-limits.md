# Límites de registro y login

La aplicación limita peticiones por dirección de conexión y operación. Las cuotas
se consumen tanto con respuestas correctas como con errores de credenciales,
validación o JSON. No se reinician al acertar una contraseña.

| Operación | Cuota inicial | Ventana desde el primer intento |
|---|---|---|
| POST /api/auth/login | 20 peticiones | 60 segundos |
| POST /api/auth/register | 10 peticiones | 900 segundos |

Al agotar una cuota devuelve 429, Retry-After (segundos redondeados hacia arriba),
Cache-Control: no-store y `{"error":"Demasiados intentos. Inténtalo de nuevo más tarde"}`.
Las cuotas de registro y login son independientes. Agotar login no bloquea el
inventario ni otras rutas con un JWT válido. Métodos HTTP no soportados no cuentan.

## Implementación y configuración

AuthRateLimitConfig aplica un interceptor MVC al método de AuthController que se
va a ejecutar, antes de deserializar/validar argumentos. La identidad del handler,
no una comparación de la URL sin contexto, decide qué cuota se usa.
AuthRateLimiter usa reloj monotónico, ventanas fijas y acceso sincronizado para
que peticiones concurrentes no superen la cuota. No almacena correos ni contraseñas.

security.auth-rate-limit en application.yaml permite ajustar login-limit,
login-window-seconds, registration-limit, registration-window-seconds y max-buckets.
Todos deben ser positivos o el arranque falla. max-buckets (10000 por defecto)
limita el número total de pares dirección/operación en memoria. Al necesitar espacio
se eliminan ventanas caducadas; no se expulsan cuotas activas para permitir eludir
el límite. Sin capacidad disponible, las nuevas claves reciben 429 hasta la primera
caducidad. Los clientes con cuota ya creada conservan su contador.

Se usa getRemoteAddr y server.forward-headers-strategy=none. X-Forwarded-For y
Forwarded enviados por un cliente no cambian la cuota. Tras un proxy o una NAT,
varios usuarios pueden compartir dirección y cuota. Antes de un despliegue detrás
de proxy, configurar límites en el proxy o un esquema explícito de proxies de
confianza; no habilitar confianza ciega en esas cabeceras.

## Alcance y pruebas

Es una protección básica para una única instancia: los contadores son locales,
se pierden al reiniciar y no se comparten entre réplicas. No impide ataques desde
muchas direcciones ni sustituye protección de tráfico en el despliegue. No bloquea
cuentas por correo ni promete resolver todos los escenarios de fuerza bruta.

Cinco tests unitarios verifican caducidad y Retry-After sin dormir, independencia,
memoria acotada, concurrencia y configuración inválida. Seis tests HTTP cubren 429,
registro válido e inválido, cabeceras falsas, context path y acceso JWT no afectado.
El perfil general de tests aumenta las cuotas; los tests específicos usan límites
de 2 en su propia base H2. Suite total: 99 tests sin fallos, errores ni omisiones.

scripts/verify_docker.py prueba también los límites por defecto con Tomcat real,
PostgreSQL y Docker, incluidas cabeceras falsas y JWT válido tras agotar login.
La misma ejecución conserva las pruebas de concurrencia y los tres ciclos de
persistencia. Ninguna prueba utiliza cuentas o bases reales del usuario.
