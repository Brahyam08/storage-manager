# Registro, login y autenticación JWT

## Registro y login

POST `/api/auth/register` requiere username no vacío (máximo 255 caracteres), email
válido (máximo 255) y password de al menos 8 caracteres y como máximo 72 bytes UTF-8,
el límite utilizado por BCrypt. Se validan campos antes de guardar o autenticar.
La contraseña se conserva exactamente, incluidos espacios exteriores; no se recorta
ni se trunca. Un carácter Unicode puede ocupar varios bytes.

Se recortan espacios exteriores de username/email. Los nuevos correos se guardan
en minúsculas. El username sigue distinguiendo mayúsculas, el correo no. El registro
siempre asigna VIEWER, ignora roles enviados por el cliente y devuelve un DTO con
id, username, email y role. Nunca devuelve la contraseña ni su hash.

Un username o email ocupado devuelve 409 con «No se puede registrar esa cuenta».
La comprobación previa y las restricciones únicas de la base protegen también
registros simultáneos; no se expone cuál de los campos coincide ni el error SQL.

POST `/api/auth/login` valida email/password y devuelve token. Cuenta inexistente y
contraseña incorrecta reciben el mismo 401 con «Credenciales incorrectas». El login
no distingue mayúsculas en el correo, y el JWT usa el correo real de la cuenta.
Las cuentas anteriores mantienen su correo original y pueden usar contraseñas
anteriores de menos de ocho caracteres: el mínimo nuevo solo aplica al registro.
Si datos antiguos contienen dos correos que solo difieren en mayúsculas se rechaza
la autenticación sin elegir una cuenta arbitraria; requiere revisión manual.

## JWT

Las rutas protegidas requieren `Authorization: Bearer <token>`.
Si falta autenticación o el token está vacío, malformado, caducado, aún no es válido,
tiene una firma incorrecta, carece de sujeto/caducidad o identifica una cuenta que
no existe, la respuesta es 401 con cabecera `WWW-Authenticate: Bearer` y cuerpo
`{"error":"Autenticación requerida"}`. No expone firmas, fechas, correos o excepciones.

La firma y campos obligatorios se comprueban antes de buscar la cuenta. El filtro
solo captura errores de token/cuenta inexistente; no envuelve al controlador.
La sesión es stateless: una petición válida no autentica las siguientes.
Los endpoints públicos siguen disponibles aunque el cliente arrastre un Bearer
inválido; puede volver a iniciar sesión con sus credenciales. No hay refresh tokens
ni revocación. Una cuenta autenticada sin permiso recibe 403.

## Errores

- Validación, JSON ilegible y parámetros inválidos: 400 sin valores rechazados ni
  detalles del deserializador. La validación conserva mensajes por campo.
- Credenciales incorrectas: 401 genérico; autorización denegada: 403 genérico.
- Recursos ausentes/inaccesibles: 404 explícito de los servicios.
- Conflictos de datos: 409 sin tablas, SQL, restricciones o valores internos.
- Errores RuntimeException inesperados: 500 con «Error interno del servidor».
  Ya no se convierten indiscriminadamente en 404 ni se devuelve ex.getMessage().

## Limitación de peticiones

Login y registro tienen cuotas separadas por dirección: 20 por 60 segundos y 10
por 900 segundos, respectivamente. Se devuelve 429 y Retry-After al agotarlas.
Se cuentan peticiones correctas e inválidas. El inventario con JWT sigue disponible.
Ver `auth-rate-limits.md` para configuración, NAT/proxies y límites de una instancia.

## Verificación y límites

Trece pruebas HTTP cubren JWT; ocho de autenticación cubren registro/login, hash,
roles, duplicados, normalización, correos antiguos, 72 bytes Unicode y errores.
Cinco pruebas MVC comprueban el manejo de errores. Once pruebas adicionales cubren cuotas. Suite total: 101 tests H2 aprobados.
La verificación Docker/PostgreSQL añade validación, registros simultáneos, errores
genéricos de credenciales y fallos de escritura, además de persistencia y concurrencia.

JWT_SECRET es externo y el secreto de fixtures es solo de tests. La consulta OSV
del grafo Maven se documenta en `dependency-review.md`; siguen pendientes
migraciones de producción y
configuración de despliegue remoto. Esto no equivale a una auditoría completa.
