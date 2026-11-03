# Plan para una demo de portfolio

Objetivo: completar el recorrido útil antes del 21 de noviembre de 2026,
reservando margen para corregir problemas y ensayar la entrevista.
No es una garantía de contratación. Las candidaturas pueden avanzar en paralelo.

## Entregas pequeñas, por orden

1. Casas, miembros e invitaciones: implementadas y con tests H2.
2. Ubicaciones por casa, jerarquía y ruta completa: implementadas y con tests H2.
3. Objetos privados/compartidos y acceso por casa: implementados con pruebas de
   búsqueda, ID, edición, movimiento e historial; rollback y concurrencia en H2.
4. Angular con TypeScript: registro/login, casas e invitaciones, ubicaciones,
   inventario con búsqueda y formularios, movimiento e historial. Diseño móvil.
5. Docker/PostgreSQL: verificados con datos sintéticos, concurrencia, rollback y
   persistencia tras recrear contenedores. Pendientes demo completa, datos de
   presentación y despliegue. Registro/login, errores genéricos y cuotas básicas por dirección ya verificados.
   Dependencias Maven y recursos HTTP de Swagger revisados; ver dependency-review.md.
6. README de portfolio, capturas, guion de demo y decisiones técnicas explicables.

## Distribución orientativa del tiempo

- 3–6 de noviembre: backend doméstico y permisos.
- 7–12 de noviembre: recorrido Angular completo.
- 13–16 de noviembre: integración, PostgreSQL/Docker y correcciones.
- 17–20 de noviembre: documentación, demo, accesibilidad básica y ensayo.

Quedan fuera de esta versión OAuth, planos, fotos, cantidades repartidas entre
ubicaciones y permisos privados por persona. Verificar PostgreSQL, autenticación, errores y despliegue antes de publicar
una demo con datos reales. Se permiten staging y commits revisados; el push queda a cargo del usuario.

Cada entrega termina con pruebas pertinentes y un diff revisable, conservando los
cambios previos. Se prioriza código sencillo que el autor pueda explicar.

## Práctica de mantenimiento acordada

Tras terminar y desplegar una versión estable se prepararán ejercicios de fallos
realistas en una rama separada. Cada ejercicio tendrá síntoma y comportamiento
esperado; el usuario investigará y escribirá una prueba, con pistas progresivas si
las necesita. No introducir fallos intencionales en la demo ni debilitar autenticación
o privacidad. No se ha creado aún ninguna rama ni ejercicio.
