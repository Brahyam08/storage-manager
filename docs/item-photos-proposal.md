# Propuesta: foto opcional de un objeto

Estado: propuesta del 4 de noviembre de 2026. No hay subida de fotos implementada.
El usuario pide estudiar la función mientras revisa la web; no se modifica la demo.

## Primera entrega

Una foto de portada por objeto, opcional. Miniatura en lugar del símbolo actual
en la lista y vista ampliable en el detalle. Añadir, sustituir y quitar desde la
gestión del objeto, sin mezclarlo con consumo o movimientos. La subida viene
después de crear el objeto: si falla, el objeto permanece y se permite reintentar.
Seleccionar archivo, previsualizar y confirmar; cancelar no sube ni cambia nada.

Desde móvil, usar el selector de imágenes del sistema; permitir escoger de la
biblioteca y estudiar un botón aparte para tomar foto sin obligar a usar cámara.
El navegador decide cómo presenta estas opciones. Probar cámara/biblioteca en
Safari iPhone y Chrome Android reales. HEIC/HEIF y orientación deben comprobarse
expresamente: no prometer conversión automática ni admitir formatos no probados.

## Almacenamiento recomendado para la beta

Tabla separada de fotos en PostgreSQL, una fila por objeto y bytes de imagen
optimizada; no incluir binarios/base64 en listados JSON. Ventajas para este tamaño:
misma persistencia/backup local que los objetos, sustitución transaccional y sin
servicio de archivos adicional. Sigue siendo almacenamiento en el ordenador que
ejecuta PostgreSQL; no es almacenamiento offline de la PWA ni cifrado de campos.

El espacio ocupado aumenta con cada foto. Proponer un máximo almacenado de
512 KiB por foto, con resolución reducida. Vigilar tamaño total y cuotas del futuro
hosting. Si crece, evaluar almacenamiento de objetos privado; no dar por hecho
que un alojamiento gratuito conserva archivos escritos dentro del contenedor.

## API y permisos propuestos

- GET /api/items/{id}/photo: mismos permisos de lectura que el objeto actual.
- PUT /api/items/{id}/photo: multipart, solo propietario autorizado para editar.
- DELETE /api/items/{id}/photo: solo propietario; quitar foto no elimina el objeto.
- Listado devuelve solo disponibilidad/versión de foto, obtenida sin consultas
  individuales por objeto ni carga de bytes. Miniaturas se solicitan cuando sean visibles.

Comprobar acceso en cada petición: una foto privada no se sirve como recurso
estático público. IDs inexistentes y ajenos mantienen la misma respuesta404.
No conceder acceso por conocer la URL ni por ser administrador global.
Angular solicita el blob con el interceptor Bearer existente; nada de JWT en URL.
Revocar object URLs al sustituir, salir o cerrar sesión. Respuestas privadas sin
caché persistente (Cache-Control: no-store); no añadir fotos a cachés de la PWA.
Cambio de visibilidad del objeto afecta también a su foto en peticiones futuras;
no permite recuperar copias ya vistas, descargadas o capturadas por otro usuario.

## Límites y validación

Propuesta inicial: JPEG/PNG y hasta5 MiB de entrada; rechazar SVG, animaciones y
archivos no decodificables. Verificar contenido real en servidor, no solo extensión,
Content-Type o accept del selector. Comprobar dimensiones antes de decodificar y
acotar recursos/concurrencia según memoria disponible. Normalizar orientación,
redimensionar y reexportar sin copiar EXIF/GPS ni nombres originales. Comprobar
con fixtures que los metadatos desaparecen; no asumirlo por cambiar extensión.

Validar autenticación/propiedad antes del procesamiento costoso; configurar límite
multipart y respuestas comprensibles. Un fallo de validación o almacenamiento
conserva la foto anterior. Sustitución y borrado deben coordinarse con borrado del
objeto y cambios de permisos. No registrar contenido de fotos, tokens o metadatos.

## Verificación necesaria antes de entregar

- Dueño sube/sustituye/quita; otro miembro no modifica. Privada inaccesible para
  tercero, incluso ADMIN y con URL conocida; compartida solo para miembros.
- Cambio de privada/compartida afecta acceso sin depender del estado del frontend.
- Persistencia después de reiniciar/recrear contenedor y restauración desde backup.
- Archivo falso, roto, enorme, dimensiones excesivas y EXIF/GPS; foto previa intacta
  ante errores y peticiones simultáneas. Probar cuotas si se habilita beta externa.
- Lista fluida, imagen ausente/rota, carga/reintento, teclado, texto alternativo y móvil.
- Cámara/biblioteca reales, orientación y formatos de iPhone: pendientes, no probados.

## Fuentes

[OWASP: File Upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)
y [HTML Media Capture](https://www.w3.org/TR/html-media-capture/).
La arquitectura concreta anterior es una propuesta para este repositorio, no una
certificación de seguridad ni una funcionalidad disponible.
