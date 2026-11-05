# Seguridad, privacidad y almacenamiento

Revisión acotada del 3 de noviembre de 2026. Describe la implementación actual y
sus límites; no es una auditoría integral ni una garantía de ausencia de fallos.

## Dónde están los datos ahora

La interfaz Angular llama a la API Java y esta guarda el inventario en PostgreSQL.
En el entorno local, PostgreSQL corre en Docker en el ordenador del usuario y
conserva sus datos en un volumen. No se guarda el inventario en localStorage ni
IndexedDB. El perfil se mantiene en memoria y se verifica con la API antes de abrir
el inventario después de recargar o relanzar la app. No se guarda la contraseña.

Por defecto el token JWT se guarda en sessionStorage de la pestaña. Si marcas
**Mantener sesión en este dispositivo** al entrar, se guarda solo ese token en
localStorage para poder recuperar la sesión al cerrar y volver a abrir la PWA.
La opción es voluntaria y está desmarcada inicialmente. El servidor mantiene su
caducidad de 24 horas: cerrar/abrir o recordar no renueva el token. Debes usar la misma
URL (protocolo, nombre/IP y puerto); cada origen tiene su almacenamiento separado.

Cerrar sesión o un 401 confirmado retira el token correspondiente. Un error de red
0/5xx conserva la credencial y permite reintentar la verificación sin contraseña.
Las respuestas de un login antiguo no pueden reactivar una sesión cerrada. Entre
ventanas, invalidar un token viejo no borra el token nuevo que haya sustituido su
recuerdo. Un cambio de la sesión persistida cierra la vista anterior en las ventanas
que la usaban, sin cambiarles de cuenta silenciosamente. Las sesiones temporales de
otras pestañas permanecen independientes.

sessionStorage y localStorage son accesibles al JavaScript del mismo origen:
no protegen ante XSS ni equivalen a cookies HttpOnly. La persistencia amplía el tiempo
durante el que una credencial puede quedar en ese dispositivo; úsala en uno de
confianza y pulsa Cerrar sesión al dejarlo. No se guarda el perfil, la contraseña
ni el inventario junto al token. Cookies HttpOnly requieren otro diseño de backend
y protección CSRF; no forman parte de esta entrega.

Si el navegador bloquea guardar el token persistente, la app intenta conservarlo
solo en la pestaña o memoria y muestra un aviso. Si el navegador borra todos los
datos del sitio o se usa navegación privada, puede ser necesario entrar de nuevo.
La comprobación final del cierre y relanzamiento de PWA sigue requiriendo iPhone real.

## Qué significa Privado

Un objeto es privado por defecto. El backend usa el usuario autenticado para
limitar listas, filtros, acceso por ID, edición, consumo, movimiento e historiales.
La identidad del propietario no se toma de un ownerId enviado por el cliente.
Ni otro miembro ni el creador de la casa con rol ADMIN de la aplicación pueden
leer un objeto privado ajeno. Compartir es una opción explícita del propietario.

Las ubicaciones pertenecen a la casa y son visibles a sus miembros. Sus nombres,
descripciones y rutas no son una caja privada. Un nombre de ubicación sensible
podría revelar información aunque los objetos guardados dentro sean privados.

Privado es una regla de autorización de la API. Una persona con acceso suficiente
al servidor, al proceso de la API, a PostgreSQL o a una copia de la base puede leer
los campos almacenados. El rol ADMIN de la aplicación y el administrador del
servidor son capacidades distintas. La implementación actual no promete que
únicamente el creador pueda descifrar los datos frente a quien opera el servidor.

## Inyección y contenido almacenado

Las consultas JPQL revisadas usan parámetros enlazados. La concatenación usada
para búsquedas LIKE forma el patrón de búsqueda, no SQL a partir de texto del
usuario. Los comodines `%` y `_` pueden ampliar coincidencias dentro del conjunto
autorizado, pero no deben ampliar permisos. Los IDs se reciben como números.

Angular muestra los datos revisados mediante interpolación y controles de
formulario. No se encontraron innerHTML, bypassSecurityTrust, eval ni persistencia
de credenciales en el código de frontend revisado. Esto no cubre automáticamente
rutas futuras, extensiones del navegador, dependencias comprometidas o scripts
introducidos en el despliegue.

La autenticación usa Bearer en Authorization. El filtro no autentica con cookies;
CSRF está deshabilitado para ese contrato actual. Cambiar a autenticación por
cookies requerirá revisar CSRF y la configuración de cookies. Las respuestas
privadas comprobadas y los accesos denegados incluyen Cache-Control no-store y
X-Content-Type-Options nosniff. Falta definir CSP, HTTPS y cabeceras del servidor
que alojará la interfaz antes de publicar.

## Verificación realizada

- 108 tests Java pasan. Se añadieron regresiones de valores SQL maliciosos en
  búsqueda/categoría, IDs malformados y cabeceras de cache de respuestas privadas.
- Las pruebas previas cubren privacidad entre propietarios, miembros, ADMIN de
  aplicación y ajenos, incluyendo rutas de historial y mutaciones.
- 33 tests Angular pasan. Se añadieron nombres, descripciones y rutas de historial
  con etiquetas img/script: permanecen como texto, sin crear elementos ejecutables.
- Se revisaron las consultas de acceso y los usos de renderizado/almacenamiento.
  Los tests Java nuevos usan H2; no se ha hecho una prueba de penetración ni se ha
  repetido el verificador Docker para este cambio exclusivo de tests/documentación.
- No se cambiaron almacenamiento, cifrado, permisos, servicios ni datos reales.
  No se revisaron en esta entrega vulnerabilidades nuevas de dependencias/imágenes.

## Opciones para información de alto valor

| Objetivo | Qué implica |
| --- | --- |
| Todo en el ordenador | La API y PostgreSQL locales actuales ya sirven de base. Quedan protección del dispositivo, backups y requisitos de acceso local. |
| Datos solo en el navegador | Requiere persistencia local, cifrado, claves, exportación/recuperación y decidir qué ocurre al borrar datos del navegador. No hay sincronización automática con otros dispositivos. |
| Web alojada y contenido solo legible por el propietario | Requiere cifrar en el cliente con claves que el servidor no controle, definir metadatos expuestos, recuperación, cambios de contraseña y cómo compartir. Afecta búsqueda y gestión de hogares. |

Guardar en local no equivale a cifrar ni a aislar de scripts del mismo origen.
El cifrado de cliente tampoco evita que código malicioso lea datos mientras el
usuario los tiene desbloqueados. No introducir criptografía casera ni migrar datos
existentes antes de acordar el modelo de amenazas y la recuperación de claves.
Para una demo puede mantenerse inventario ficticio y dejar explícito este alcance.

Referencias: [OWASP: almacenamiento en navegador](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html),
[autorización](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html),
[almacenamiento criptográfico](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
y [seguridad de base de datos](https://cheatsheetseries.owasp.org/cheatsheets/Database_Security_Cheat_Sheet.html).
