# Beta HTTPS con Tailscale Funnel en macOS

La beta usa proyecto Docker `storage-beta`, archivo local ignorado `.env.beta`,
base/volumen y secreto JWT distintos. Web solo en `127.0.0.1:8081`, PostgreSQL solo
en `127.0.0.1:55001`. La demo personal sigue en8080 y nunca es el destino del túnel.
El archivo de entorno debe mantenerse con permiso600 y fuera de Git.

## Cuenta e instalación

1. Descargar la variante Standalone desde [Tailscale para Mac](https://tailscale.com/download/mac).
   No instalar simultáneamente la versión App Store.
2. Aprobar personalmente la extensión/configuración de red que macOS solicite e
   iniciar sesión con una cuenta personal. Comprobar plan Personal gratuito, sin
   activar prueba empresarial. [Planes](https://tailscale.com/pricing).
3. La CLI se puede usar con la ruta incluida en la aplicación:

```sh
/Applications/Tailscale.app/Contents/MacOS/Tailscale status
```

No requiere abrir puertos en el router. Los visitantes de Funnel usan su navegador,
sin instalar Tailscale. Funnel es público: conocer el enlace no reemplaza el login.

## Antes de activar

- Crear las cuentas individuales de testers mientras la instancia esté solo local;
  después cerrar el registro otra vez. No usar una cuenta compartida entre todos.
  Ver [procedimiento de provisión local](private-beta-security.md).
- Comprobar beta: API anónima401, registro403, docs desactivadas y cabecera CSP.
- Verificar que `.env.beta` indica perfil beta y bind127.0.0.1, y que los datos son
  ficticios. No copiar el volumen ni copias de la demo personal.
- Crear/verificar un backup privado de la beta con scripts/local_backups.py.
- Revisar `Tailscale funnel status`: no sustituir ni resetear rutas ajenas existentes.

## Activación acompañada

Este comando publica únicamente la web beta. Ejecutarlo después de las comprobaciones
anteriores y revisar la URL/puerto que Tailscale muestra:

```sh
/Applications/Tailscale.app/Contents/MacOS/Tailscale funnel --https=443 http://127.0.0.1:8081
```

La primera vez puede proporcionar un enlace para habilitar Funnel, HTTPS y MagicDNS.
Abrirlo e iniciar sesión personalmente; revisar la autorización que pide. La
aprobación de Funnel es lo que permite acceso desde Internet a este servicio.
Tailscale obtiene el certificado válido; el tramo HTTP de origen queda en loopback.
No instalar un certificado manual en los iPhone de testers. No se publica PostgreSQL.

Primero se usa en primer plano: no añadir --bg hasta haber comprobado el resultado.
Para detener esta prueba, Ctrl+C. Comprobar también el estado; si sigue una ruta
configurada, el apagado específico es:

```sh
/Applications/Tailscale.app/Contents/MacOS/Tailscale funnel --https=443 off
/Applications/Tailscale.app/Contents/MacOS/Tailscale funnel status
```

No usar reset si puede haber otros servicios de Tailscale Serve/Funnel configurados.
--bg mantiene la publicación tras reinicios; sería una elección posterior explícita.

## Prueba externa

Abrir la URL HTTPS desde iPhone con Wi-Fi apagado: certificado sin advertencia,
login de tester, alta/modificación de datos ficticios, privacidad entre dos cuentas,
CSV y sesión/PWA. Abrir `/api/users/me` sin sesión debe devolver401; el registro
permanece cerrado. Comprobar que la CSP sigue llegando y no rompe estilos.
Con HTTPS ya se puede verificar instalación PWA; sigue necesitando conexión.

El Mac, Docker y Tailscale deben seguir activos. La suspensión o una caída de red
interrumpen la demo. Funnel tiene límites de ancho de banda y sigue en beta.
Las cuotas de autenticación actuales se comparten por la dirección del proxy;
no activar confianza indiscriminada en X-Forwarded-For. Para esta beta pequeña se
conservan cuotas globales conservadoras: login20/minuto, registro10/15minutos.

[CLI oficial](https://tailscale.com/docs/reference/tailscale-cli/funnel) y
[variantes para macOS](https://tailscale.com/docs/concepts/macos-variants).

## Carga desde datos móviles

El perfil beta comprime HTML, CSS y JavaScript a partir de1KB; las respuestas JSON
privadas quedan fuera de esa compresión. Los JS/CSS públicos cuyo nombre contiene
un hash reciben caché de un año e immutable, solo si responden200 con tipo JS/CSS.
Errores, HTML y API conservan no-store. Al cambiar el build, cambia el nombre del
archivo, por lo que la siguiente carga del HTML solicita la versión nueva.

Medición inicial del Mac: chunk principal267843bytes sin comprimir, aproximadamente
82910bytes con gzip. La latencia observada desde el propio Mac no reproduce la ruta
real del iPhone en red móvil; repetir allí antes de dar por resuelta una carga lenta.
