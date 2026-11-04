# Reutilizables individuales y grupos

Al crear un reutilizable, «Cómo lo gestionas» permite elegir un objeto individual
(por ejemplo, un teclado concreto) o un grupo de piezas iguales (por ejemplo,
varias sillas). Los nuevos formularios proponen individual; consumibles conservan
cantidades. Los registros existentes NO se convierten automáticamente.

## Objeto individual

Representa una pieza. Lista y detalle muestran Disponible o En uso en lugar de
unidades; creación/edición no muestran el campo de cantidad. No permite Añadir
unidades y la API también rechaza reposiciones409. Retirar y devolver conserva
el mismo objeto y su historial. Una segunda pieza distinguible se crea aparte.
Internamente quantity sigue siendo1 para reutilizar los bloqueos y el contrato
de uso/devolución existentes, sin introducir un segundo contador.

## Grupo

Conserva cantidad, disponibles/en uso y reposición. Un grupo puede tener solo una
pieza: no se deduce el modo a partir de su cantidad. Para cambiar un registro
existente de una pieza: Editar objeto → Cómo lo gestionas → Un objeto individual.
No se permite convertir un grupo con varias piezas reduciéndolo implícitamente;
la API responde409 y conserva el total. La interfaz deshabilita esa opción cuando
el total actual no es1. Cambiar de modo no borra historiales.

## API y compatibilidad

ItemRequest/ItemResponse incluyen `individual`. Solo se admite true con
kindREUSABLE yquantity1; otras combinaciones se rechazan400. Omitirlo/null en
actualización conserva el modo actual; crear sin ese campo sigue creando grupos
para compatibilidad con clientes anteriores. Pasar a consumible permite false o
campo omitido y mantiene las restricciones previas de cambio de tipo/historial.
Solo el propietario puede cambiar el modo, igual que otras ediciones del objeto.

Nueva columna items.individual boolean notnull defaultfalse: migración aditiva,
sin adivinar datos del usuario. Copia antes de actualizar una base real; migraciones
versionadas siguen pendientes. Privacidad, sesión y permisos no se amplían.

## Verificación

136 tests Java y72 Angular, build de producción y Docker/PostgreSQL completo.
Pruebas de invariantes, permisos, grupo de una pieza, conversión sin pérdida,
uso/devolución individual, rechazo de reposición y persistencia. PostgreSQL
comprueba que los registros anteriores mantienen individualfalse.
Prueba visual con fixtures: convertir grupo de una pieza a individual, retirar,
devolver y revisar estado en viewport390. No equivale a probar Safari iPhone real.
