# Revisión para la primera beta

La suite automática no sustituye las pruebas en dispositivos reales. Usar cuentas
ficticias y una base de prueba separada del inventario personal. No publicar la
beta hasta comprobar acceso privado, persistencia y recuperación de errores.

## Primera pasada — 4 de noviembre de 2026

| Recorrido | Resultado y alcance |
| --- | --- |
| Acceso y consulta del inventario | Comprobado en navegador con datos ficticios de Docker aislado. |
| Actualizar sin recargar | Comprobado: mantiene el filtro de reutilizables y vuelve a consultar la API. |
| Error de carga y reintento | Test HTTP Angular: error 503, reintento, sin peticiones duplicadas y filtro conservado. |
| Recargar con sesión vigente | Comprobado en navegador: vuelve al inventario sin pedir contraseña. |
| Cerrar sesión | Comprobado en navegador: vuelve a acceso; no permite recuperar inventario protegido. |
| Detalle reutilizable único | Revisado en vista estrecha: botón directo sin cantidad. |
| Safari iPhone real y teclado | Pendiente de prueba en dispositivo; no equivale a viewport reducido. |
| Chrome Android real y teclado | Pendiente. |

68 tests Angular y compilación de producción correctos en esta pasada.
No se ha repetido la suite Java: no hay cambios de backend.

## Navegación de perfil y objetos — 4 de noviembre de 2026

Comprobado con fixtures Docker: pulsar la ubicación dentro de una tarjeta abre
su página, recargar conserva el objeto y la sesión, y el enlace de vuelta conserva
el filtro privado. El avatar abre perfil con nombre/correo propios; viewport390
sin desbordamiento horizontal. Perfil solo de consulta. 131 tests Java y63 Angular
pasan; las rutas nuevas no abren endpoints de inventario anónimos.

## Jerarquía del detalle

Las acciones de uso e historial van antes de la gestión del objeto; eliminar queda
al final de la gestión. Existencias destacadas a la derecha del nombre, con total
y disponibles/en uso separados en reutilizables. Consumible revisado en escritorio
y viewport390 sin desbordamiento horizontal; pruebas de Safari real pendientes.

## Siguientes recorridos

- iPhone: mandar Safari a segundo plano y volver, con conexión estable e interrumpida; distinguir recuperación y caducidad.
- Registro, validaciones, contraseña incorrecta y sesión caducada.
- Primera casa vacía, unirse por invitación, código inválido y renovación.
- Crear y editar ubicaciones anidadas; nombres largos y caracteres especiales.
- Crear/editar objetos privados y compartidos; búsquedas sin resultados y agotados.
- Reponer unidades desde agotado, comprobar vista previa e historial; consumo simultáneo en otra sesión.
- Consumir parte del stock, retirar/devolver un reutilizable y consultar historiales.
- Mover objetos y comprobar las restricciones cuando hay unidades en uso.
- Archivar/restaurar objeto con historial; restaurar ubicación antes del objeto cuando ambas están archivadas.
- Confirmaciones de eliminación: cancelar en la UI; borrado definitivo solo en fixtures.
- Dos cuentas: acceso privado, cambios compartidos y conflictos simultáneos.
- Red lenta o interrumpida: carga visible, botones bloqueados y recuperación sin duplicados.
- Pantallas estrechas, orientación horizontal, foco con teclado y zoom de texto.

Registrar para cada fallo: dispositivo/navegador, pasos, resultado esperado y
resultado observado. Añadir capturas solo con datos ficticios.

## Antes de invitar a testers externos

Preparar HTTPS, entorno/base separados, acceso restringido a testers, copias y
restauración comprobadas, y límites del alojamiento gratuito. La [base PWA](pwa-installation.md) ya incluye manifiesto e iconos; verificar
instalación y actualizaciones con HTTPS y móviles reales antes de darla por terminada. No cachear respuestas privadas de API
como parte del funcionamiento offline. El soporte de inventario sin conexión
queda fuera de la primera beta.

## Gestos y cierre de PWA — 5 noviembre 2026

Los campos táctiles usan al menos 16 px y la página aplica `touch-action: manipulation`
para mantener desplazamiento/pellizco y evitar el zoom por doble toque. El viewport
sigue permitiendo ampliación manual. [Comportamiento documentado por WebKit](https://webkit.org/blog/5610/more-responsive-tapping-on-ios/).
El ajuste está desplegado localmente; la comprobación iPhone del usuario sigue pendiente.

Para el relanzamiento, entrar marcando Mantener sesión en este dispositivo, cerrar
la PWA por completo y abrirla de nuevo con la misma URL. Debe verificar identidad
sin pedir contraseña mientras el token sea válido. Comprobar también caducidad de 24 h,
Cerrar sesión, almacenamiento bloqueado y modo temporal sin la casilla marcada.

La preparación del perfil cerrado y los bloqueos antes de publicar se detallan en
[seguridad de la beta](private-beta-security.md). El perfil no publica servicios ni
separa por sí solo los datos personales.

## Recorrido para un tester con Android

Sin teléfono Android real no dar por validados teclado, gestos ni instalación.
La revisión de anchos en navegador comprueba el diseño, pero no sustituye el dispositivo.
Pedir modelo del móvil, versión de Android y versión de Chrome junto al resultado.

1. Abrir la beta HTTPS con datos móviles y entrar con su cuenta individual.
2. Crear una casa, ubicación y objeto ficticios; editar un nombre largo y comprobar
   que el teclado no tapa guardar, no aparece zoom involuntario ni scroll horizontal.
3. Consumir unidades y retirar/devolver un reutilizable individual.
4. Usar el menú de Chrome para añadir/instalar, si se ofrece. No asumir funcionamiento
   offline: la app sigue necesitando conexión.
5. Con Mantener sesión marcado, cerrar y relanzar; comprobar también Cerrar sesión.
6. Dejar abierta la app durante una entrega posterior: ver el aviso de versión,
   conservar un borrador sin recarga automática y recargar solo después de guardar.
7. Enviar pasos/captura si falla. Usar exclusivamente datos ficticios.

El emulador Android puede aportar comprobaciones posteriores, pero tampoco certifica
el comportamiento de todos los teléfonos físicos. Prueba Android real: pendiente.
