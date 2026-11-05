# Exportar resultados a CSV

**Exportar CSV**, junto a Actualizar inventario, descarga solo los objetos visibles
en la casa actual. Respeta nombre, ubicación, tipo/visibilidad y Mostrar archivados.
Mientras cambia la búsqueda, carga el inventario o hay un error, no permite exportar
resultados anteriores. Si no hay resultados, el botón está deshabilitado.

El archivo se genera en el navegador, sin una petición de exportación al servidor.
Incluye nombre, categoría, ubicación, tipo, formato individual/por unidades,
existencias, disponibilidad, uso, visibilidad, archivo y descripción. Los objetos
sin ubicación se identifican como tales. No incluye usuarios, contraseñas, tokens
ni identificadores internos. Los objetos privados que puedas ver sí aparecen si
están dentro del resultado: el archivo debe tratarse como una copia de esos datos.

Es UTF-8 con BOM, separado por punto y coma y con saltos CRLF. Si la hoja de cálculo
no detecta el formato, elige UTF-8 y separador `;` al importarlo. Las comillas se
duplican y todos los campos se entrecomillan para conservar separadores y saltos
de línea dentro de nombres/descripciones.

Los campos que podrían interpretarse como fórmulas reciben un tabulador de texto
al principio, incluyendo signos de ancho completo y espacios previos. Esa marca
forma parte del dato exportado. Es una medida para visualizar datos en hojas de
cálculo, no un formato para reimportación exacta ni una garantía universal para
todos los programas y posteriores guardados. [Referencia de CSV Injection de OWASP](https://community.owasp.org/attacks/CSV_Injection).

El CSV no sustituye las [copias PostgreSQL](local-backups.md): no contiene todos los
usuarios ni historiales y no se puede importar a la app. La descarga en Safari/PWA
depende del navegador; comprobar en un dispositivo real si guarda el archivo o
presenta las opciones de abrir/compartir.
