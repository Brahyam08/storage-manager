# Base de instalación en móviles

Primera entrega: manifiesto, iconos PNG y guía en Mi perfil. No es todavía una
beta instalada/verificada en iPhone y Android reales, ni un despliegue público.
No añade service worker, caché offline, notificaciones push o dependencias.

## Comportamiento

- `manifest.webmanifest`: identidad estable `/`, nombre Storage Manager, arranque
  `/inventario`, ámbito `/`, idioma español y display standalone.
- Iconos192/512 para instalación, icono maskable512 con margen para recortes y
  apple-touch-icon180. Siguen el símbolo de cuatro cuadrados del favicon actual.
- Mi perfil explica el menú de Safari/Chrome y muestra el límite de HTTP local
  si `window.isSecureContext` es falso. No fuerza permisos ni instalación.
- El inventario necesita conexión con la API. No se persisten respuestas privadas
  en Cache Storage ni se añade una caché nueva de credenciales/datos.
- La sesión instalada puede ser distinta a la pestaña del navegador; puede pedir
  acceso al abrir por primera vez. La caducidad y autorización siguen en la API.

El manifiesto y los iconos son recursos públicos GET/HEAD. No contienen datos de
usuarios. POST/DELETE sobre esos recursos siguen protegidos y las APIs conservan
sus permisos. Las direcciones de iconos desconocidos devuelven404, no la SPA.

## Instalación y límites actuales

La promoción de instalación en Chrome requiere HTTPS, además de manifiesto e
iconos válidos; su aparición depende también del navegador, instalación previa
y criterios de interacción. No se promete un botón automático de instalación.
Los service workers no son requisito para la instalación desde el menú de Chrome
moderno; añadir uno se decidirá por una necesidad concreta y con política de
actualizaciones y privacidad propia.

La excepción localhost permite ciertas pruebas de contexto seguro en el propio
ordenador. `http://192.168.x.x:8080` desde otro dispositivo no equivale a localhost:
la web sigue disponible por LAN, pero no valida la instalación PWA completa.
No desactivar advertencias del navegador ni instalar certificados improvisados
para declarar superada esta comprobación. HTTPS de la beta queda pendiente.

En iPhone: abrir la dirección en Safari → Compartir → Añadir a pantalla de inicio;
si el sistema lo ofrece, activar Abrir como app web. En Android: abrir Chrome y
buscar Instalar aplicación/Añadir a pantalla de inicio en el menú. Los nombres y
las opciones varían según versión. No implica publicar en App Store o Google Play.

## Actualizaciones

Sin service worker, no se conserva una versión de bundles en una caché propia de
la PWA: se usa el mecanismo HTTP habitual y se recarga para obtener la versión
publicada. Las sesiones abiertas no se actualizan en vivo ni se recargan por la
fuerza. No hay banner de nueva versión en esta entrega. Guardar cambios antes de
recargar. Si añadimos worker, necesitaremos comprobar actualizaciones, bundles
antiguos y navegación offline antes de habilitarlo.

## Comprobación

Docker verifica manifiesto con MIME application/manifest+json, referencias HTML,
identidad/ruta de inicio, dimensiones reales y firma PNG de cada icono, protección
de APIs y rechazo de mutaciones. Java cubre lookalikes y verbos no permitidos.
Revisar visualmente icono y guía con datos ficticios en la imagen empaquetada.

Pendiente con una dirección HTTPS y dispositivos reales:

- Añadir a pantalla de inicio en Safari iPhone y Chrome Android.
- Icono legible después del recorte del sistema y nombre mostrado correcto.
- Abrir standalone, iniciar sesión, navegar a objeto, volver y cerrar sesión.
- Abrir una segunda vez, sesión caducada y acceso privado entre cuentas.
- Actualizar despliegue y comprobar recarga sin formularios perdidos por sorpresa.
- Cortar la conexión: no presentar inventario antiguo ni confirmar escrituras offline.

## Fuentes consultadas

[Chrome: criterios de instalación](https://web.dev/articles/install-criteria),
[Chrome: instalación desde menú sin fetch handler](https://developer.chrome.com/blog/update-install-criteria),
[Apple: añadir una web como app](https://support.apple.com/guide/iphone/open-as-web-app-iphea86e5236/ios).
