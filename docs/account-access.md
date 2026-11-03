# Acceso a cuentas

Una sesión permite consultar únicamente su propio perfil:

- GET `/api/users/me`: perfil de la cuenta autenticada.
- GET `/api/users/{id}`: perfil propio; cuenta ajena e inexistente devuelven
  el mismo 404 y mensaje «Usuario no encontrado».
- GET `/api/users` (también con barra final): 403, listado deshabilitado.
- DELETE `/api/users/{id}`: 403 para cualquier cuenta, incluida la propia.

El rol global ADMIN no permite consultar otras cuentas, listarlas o borrarlas.
La identidad proviene del Principal de Spring Security. La consulta por ID se
filtra en el repositorio usando también el correo autenticado. El DTO de respuesta
incluye id, username, email y role; nunca incluye contraseñas ni hashes.

Se retiran los métodos globales de listado/borrado de UserService. No se implementa
borrado propio ni cascadas: primero hay que definir qué ocurre con casas, miembros,
objetos e historial. La prohibición se aplica en SecurityConfig antes de llegar
al controlador, incluido cualquier intento de borrado de cuentas inexistentes.

Las pruebas de API utilizan JWT real, Spring Security y H2. Verifican ambos roles,
perfil propio, ocultación de cuentas ajenas, prohibición del listado y borrado,
conservación de registros y peticiones sin sesión.

Las entregas posteriores `authentication.md` e `item-privacy.md` corrigen JWT
inválidos y acceso al inventario. Siguen pendientes la integración PostgreSQL,
validación de registro/login y otras comprobaciones antes de una demo pública.
