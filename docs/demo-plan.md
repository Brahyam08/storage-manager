# Alcance del MVP y siguiente etapa

Estado revisado el 7 de noviembre de 2026. El MVP de Storage Manager está funcional
y disponible como beta HTTPS con cuentas individuales. La siguiente etapa consiste
en recoger feedback, corregir fallos y presentarlo en entrevistas.

## Dentro del MVP

Autenticación, casas e invitaciones, ubicaciones anidadas, inventario privado o
compartido, consumos, reposiciones, uso y devolución de reutilizables, movimientos,
archivo/restauración, borrado y exportación CSV. Interfaz Angular adaptable,
PostgreSQL persistente, copias verificadas y beta separada del inventario personal.

Las actualizaciones de datos se consultan periódicamente. Las nuevas versiones del
frontend ofrecen recarga voluntaria. PWA sin inventario offline; validación en
Android físico todavía pendiente.

## Prioridad ahora

1. Corregir errores reproducibles que bloqueen recorridos, pierdan datos o rompan permisos.
2. Recoger feedback de una primera ronda pequeña, incluyendo una persona con Android.
3. Revisar el [recorrido de cinco minutos](local-demo.md) y explicar una petición de extremo a extremo.
4. Mantener el README y las instrucciones reproducibles para revisión técnica.
5. Utilizar el proyecto en candidaturas mientras continúa la beta.

No añadir funciones solo para ampliar la lista de tecnologías. Clasificar cada
propuesta como fallo del MVP, mejora pequeña de uso o backlog, antes de implementarla.

## Backlog fuera del MVP

Fotos, cantidades repartidas entre ubicaciones, exportación PDF, OAuth y una rama
experimental de microservicios. Paginación y cambios de concurrencia se evaluarán
con medidas y volumen de uso. La beta no ofrece disponibilidad ni protección DDoS
garantizadas; el alcance de seguridad se describe en [sus límites](security-and-storage.md).

## Práctica de mantenimiento

Los ejercicios con fallos deliberados quedan para una rama aislada posterior.
No se introducen fallos intencionales en main ni en la beta de testers.
