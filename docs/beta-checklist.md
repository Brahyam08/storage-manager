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

52 tests Angular y compilación de producción correctos en esta pasada.
No se ha repetido la suite Java: no hay cambios de backend.

## Siguientes recorridos

- Registro, validaciones, contraseña incorrecta y sesión caducada.
- Primera casa vacía, unirse por invitación, código inválido y renovación.
- Crear y editar ubicaciones anidadas; nombres largos y caracteres especiales.
- Crear/editar objetos privados y compartidos; búsquedas sin resultados y agotados.
- Consumir parte del stock, retirar/devolver un reutilizable y consultar historiales.
- Mover objetos y comprobar las restricciones cuando hay unidades en uso.
- Confirmaciones de eliminación: cancelar en la UI; borrado definitivo solo en fixtures.
- Dos cuentas: acceso privado, cambios compartidos y conflictos simultáneos.
- Red lenta o interrumpida: carga visible, botones bloqueados y recuperación sin duplicados.
- Pantallas estrechas, orientación horizontal, foco con teclado y zoom de texto.

Registrar para cada fallo: dispositivo/navegador, pasos, resultado esperado y
resultado observado. Añadir capturas solo con datos ficticios.

## Antes de invitar a testers externos

Preparar HTTPS, entorno/base separados, acceso restringido a testers, copias y
restauración comprobadas, y límites del alojamiento gratuito. Después añadir PWA
instalable y comprobar sus actualizaciones. No cachear respuestas privadas de API
como parte del funcionamiento offline. El soporte de inventario sin conexión
queda fuera de la primera beta.
