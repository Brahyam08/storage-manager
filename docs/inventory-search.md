# Encontrar objetos por ubicación

El inventario permite combinar nombre, ubicación y tipo/visibilidad del objeto.
El selector muestra las rutas completas de la casa elegida. **Todas las ubicaciones**
incluye también los objetos sin ubicación; el filtro **Sin ubicación** permite
consultarlos por separado.

Seleccionar una ubicación muestra solo los objetos guardados directamente en ella:
elegir «Estudio» no incluye automáticamente los de «Estudio → Cajón 1».
La API recibe `locationId` junto a `houseId`, búsqueda y archivo, y aplica los mismos
permisos privados/compartidos de siempre.

Las ubicaciones archivadas aparecen identificadas. Para ver sus objetos archivados
hay que marcar **Mostrar objetos archivados**. Las ubicaciones eliminadas no aparecen.

Al abrir un objeto y volver al inventario se conservan casa, nombre buscado,
ubicación y filtros. Cambiar de casa elimina la selección de ubicación anterior y
cancela las lecturas pendientes. Si una lectura correcta confirma que la ubicación
seleccionada ya no existe, se vuelve a **Todas las ubicaciones**.

Un fallo de red al actualizar ubicaciones conserva la selección y permite reintentar;
no amplía silenciosamente el resultado a toda la casa. Un cajón sin coincidencias
muestra **Limpiar filtros**. El selector de ubicaciones consulta también archivadas;
el formulario de creación mantiene su consulta separada de destinos activos.

Verificación: tests Angular de combinación de filtros, navegación, cancelación al
cambiar de casa, eliminación de ubicación, estado vacío y recuperación de errores.
La revisión visual usa datos sintéticos en una demo Docker aislada.
