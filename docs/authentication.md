# Autenticación JWT

Las rutas protegidas requieren `Authorization: Bearer <token>`.

Si falta autenticación o el token está vacío, malformado, caducado, aún no es válido,
tiene una firma incorrecta, carece de sujeto/caducidad o identifica una cuenta que
no existe, la respuesta es 401 con:

- Cabecera `WWW-Authenticate: Bearer`.
- Cuerpo JSON `{"error":"Autenticación requerida"}`.

No se devuelven detalles de firmas, fechas, correos ni excepciones del parser.
La firma y los campos obligatorios se comprueban antes de buscar la cuenta.
El filtro captura únicamente errores de token y cuenta inexistente; no envuelve
la ejecución posterior del controlador ni oculta errores ajenos a autenticación.
La sesión es stateless: una petición válida no autentica las siguientes.

Los endpoints públicos de registro/login siguen siendo públicos aunque el cliente
arrastre un Bearer inválido. Esto permite volver a iniciar sesión con credenciales
válidas tras la caducidad de un token. No se introducen refresh tokens ni revocación.
Un usuario autenticado sin permiso sigue recibiendo 403 en las rutas deshabilitadas.

Trece pruebas HTTP con JWT real verifican estos casos, incluido login público y
peticiones consecutivas. El secreto de fixtures es exclusivamente de tests; la
aplicación requiere JWT_SECRET externo. La suite completa pasa 51 tests con H2.

Privacidad/aislamiento del inventario se incorpora en `item-privacy.md`. Pendientes:
validación de registro/login, errores generales, dependencias y despliegue.
