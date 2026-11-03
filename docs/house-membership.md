# Miembros e invitaciones

Como creador quiero compartir un código para que otra cuenta pueda unirse a mi casa.

- `POST /api/houses/{id}/invitation`: solo el creador; devuelve `{"code":"..."}`.
  Cada llamada genera un código nuevo e invalida el anterior.
- `POST /api/houses/join`: cuerpo `{"code":"..."}`; devuelve la casa y registra
  al usuario autenticado como miembro. Repetir la unión no duplica miembros.
- `GET /api/houses` y `GET /api/houses/{id}` incluyen casas creadas y compartidas.
- Una casa ajena o inexistente devuelve 404. El rol ADMIN no concede acceso.
- Los miembros no pueden generar invitaciones. Renovarlas conserva los miembros.
- El código contiene 24 bytes aleatorios, se muestra al generarlo y solo se guarda
  su hash SHA-256. Es reutilizable hasta la renovación; todavía no tiene caducidad.
- La base de datos impide duplicar pares casa/usuario. La unión bloquea la invitación
  y la renovación bloquea la casa dentro de una transacción.

Esta entrega no añade edición o expulsión de miembros, ni acceso al inventario por
casa: ubicaciones y objetos siguen pendientes. Las pruebas de API verifican creación,
unión repetida, acceso como miembro, renovación, códigos inválidos y acceso anónimo.
