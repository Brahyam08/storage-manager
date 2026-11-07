# Demostración de Storage Manager

Recorrido reproducible de aproximadamente cinco minutos con datos ficticios.
Permite comprobar búsqueda, consumo, uso de reutilizables, movimientos y privacidad
entre cuentas. No requiere ejecutar toda la suite de tests durante la demostración.

## Preparación

En una copia nueva, configura `.env` desde `.env.example` con secretos propios y ejecuta:

```bash
docker compose up -d --build
```

Abre http://localhost:8080. Para una instancia existente conserva su nombre de
proyecto Compose y archivo de entorno; no elimines volúmenes para preparar datos.
En el perfil local normal puedes registrar cuentas. La beta HTTPS tiene el
registro cerrado y requiere cuentas de prueba provisionadas previamente.

Prepara dos sesiones independientes, por ejemplo una ventana normal y otra privada,
sin marcar Mantener sesión. La segunda cuenta debe estar unida por invitación a la
misma casa ficticia «Casa demo». No compartas contraseñas, tokens ni archivos de entorno.

Crea las ubicaciones «Estudio → Cajón 1» y «Estantería 1». En el cajón añade:

| Objeto            | Tipo y formato                     | Cantidad inicial | Visibilidad            |
| ----------------- | ---------------------------------- | ---------------- | ---------------------- |
| Tornillos M3      | Consumible                         | 10               | Compartido con la casa |
| Ratón de repuesto | Reutilizable, Un objeto individual | 1                | Privado                |
| Arandelas M4      | Consumible                         | 0                | Compartido con la casa |

El ratón debe estar disponible. El recorrido modifica datos y conserva historial;
para repetirlo, usa nuevos objetos ficticios o repón las cantidades antes de empezar.

## Recorrido

| Tiempo aproximado | Acción                                                          | Resultado esperado                                                                 |
| ----------------- | --------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| 0:00–0:35         | Abrir Casa demo y sus ubicaciones                               | Se distingue la jerarquía Estudio → Cajón 1.                                       |
| 0:35–1:15         | Buscar Tornillos M3, limpiar búsqueda y filtrar agotados        | Aparecen las arandelas con cantidad cero. Limpiar el filtro antes de seguir.       |
| 1:15–2:00         | Abrir los tornillos, registrar consumo de 5 y ver consumos      | Quedan 5 unidades y se conserva el registro del consumo.                           |
| 2:00–2:45         | Volver al inventario sin filtros, retirar el ratón y devolverlo | Cambia disponible/en uso; el objeto no se consume y no pide cantidad.              |
| 2:45–3:20         | Mover los tornillos a Estantería 1 y ver movimientos            | Se trasladan las 5 unidades restantes y aparece origen/destino en el historial.    |
| 3:20–4:05         | Abrir la misma casa con la segunda cuenta                       | Puede encontrar los tornillos compartidos, pero no el ratón privado.               |
| 4:05–5:00         | Consultar arquitectura, pruebas y límites en el README          | La documentación permite relacionar cada operación con el backend y su validación. |

Los permisos se aplican en el servidor, incluidas búsquedas y accesos por ID.
Las ubicaciones y sus nombres se comparten dentro de la casa. Consultar el
[contrato de privacidad](item-privacy.md) para las reglas completas.

## Otros recorridos disponibles

- Añadir unidades y consultar el historial de reposiciones.
- Archivar y restaurar un objeto; el borrado permanente también elimina su historial.
- Eliminar una ubicación y comprobar que sus objetos permanecen como Sin ubicación.
- Exportar los resultados visibles a CSV; no sustituye una copia de la base.
- Modificar un objeto desde otra sesión: las vistas visibles comprueban cambios
  cada 15 segundos. Una acción abierta o un borrador pausa la actualización del detalle.
- Publicar una nueva versión del frontend: aparece un aviso con recarga voluntaria.
  No hay recarga forzada de formularios ni conexión WebSocket.

## Comprobaciones y alternativas

La verificación completa se ejecuta con `./scripts/verify_local.sh`, en una sesión
separada. Utiliza Docker desechable para las pruebas y no hace push ni publica imágenes.
[Instrucciones de verificación](verification.md).

Si el túnel no está disponible, usar la instancia local o las capturas. Si los datos
no están preparados, el recorrido puede consultarse con imágenes y código sin
modificar inventario real. No borrar volúmenes para resolver un fallo de acceso.

[Captura de escritorio](images/inventory-desktop.jpg) ·
[Captura móvil](images/inventory-mobile.jpg). Ambas contienen datos ficticios.

## Límites

Se necesita conexión. El operador de la base y los backups legibles quedan fuera
del aislamiento entre usuarios de la API: no hay cifrado de extremo a extremo.
La beta depende de que el Mac, Docker y el túnel estén activos. La validación en
Android físico sigue pendiente.

[Seguridad y almacenamiento](security-and-storage.md) ·
[Copias y recuperación](local-backups.md) ·
[Recorrido Android para testers](beta-checklist.md#recorrido-para-un-tester-con-android).
