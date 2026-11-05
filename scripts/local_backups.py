#!/usr/bin/env python3
"""Create private PostgreSQL backups and verify recovery in an isolated container."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
TABLES = ("users", "houses", "house_members", "locations", "items",
          "item_movements", "item_consumptions", "item_restocks", "item_uses")
COMPOSE_VARIABLES = {"POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD", "JWT_SECRET",
                     "API_BIND_ADDRESS", "API_PORT", "DB_PORT", "COMPOSE_PROJECT_NAME"}


class BackupError(RuntimeError):
    pass


def run(command, operation, **kwargs):
    try:
        return subprocess.run(command, stdout=kwargs.pop("stdout", subprocess.PIPE),
                              stderr=subprocess.PIPE, check=True, **kwargs)
    except (OSError, subprocess.CalledProcessError):
        raise BackupError(f"No se pudo {operation}. Revisa Docker y la configuración indicada.") from None


def archive_path(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise BackupError("Elige un archivo de copia existente, sin enlaces simbólicos.")
    with path.open("rb") as source:
        if source.read(5) != b"PGDMP":
            raise BackupError("La copia debe tener formato PostgreSQL custom (PGDMP).")
    return path.resolve()


def backup_database(project, env_file, output_dir):
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", project):
        raise BackupError("Indica un nombre de proyecto Compose válido.")
    env_file = Path(env_file).resolve()
    if not env_file.is_file():
        raise BackupError("El archivo de entorno indicado no existe.")
    output_dir = Path(output_dir)
    if output_dir.is_symlink():
        raise BackupError("La carpeta de copias no puede ser un enlace simbólico.")
    output_dir = output_dir.resolve()
    if output_dir == ROOT or ROOT in output_dir.parents:
        raise BackupError("Guarda las copias fuera del repositorio.")
    environment = {key: value for key, value in os.environ.items() if key not in COMPOSE_VARIABLES}
    compose = ["docker", "compose", "--project-name", project, "--env-file", str(env_file),
               "-f", str(ROOT / "docker-compose.yml")]
    running = run(compose + ["ps", "--status", "running", "-q", "db"],
                  "consultar la base de origen", env=environment).stdout.strip()
    if not running:
        raise BackupError("El servicio db del proyecto indicado debe estar iniciado. No se ha arrancado ningún servicio.")
    output_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    if stat.S_IMODE(output_dir.stat().st_mode) & 0o077:
        raise BackupError("La carpeta de copias debe ser privada (permisos 700).")
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    descriptor, temporary_name = tempfile.mkstemp(prefix=f"{project}-{timestamp}-", suffix=".partial", dir=output_dir)
    temporary = Path(temporary_name)
    destination = temporary.with_suffix(".dump")
    try:
        with os.fdopen(descriptor, "wb") as output:
            run(compose + ["exec", "-T", "db", "sh", "-c",
                'pg_dump --format=custom --no-owner --no-acl --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'],
                "crear la copia", env=environment, stdout=output)
            output.flush()
            os.fsync(output.fileno())
        archive_path(temporary)
        with temporary.open("rb") as source:
            run(compose + ["exec", "-T", "db", "pg_restore", "--list"],
                "validar el índice de la copia", env=environment, stdin=source, stdout=subprocess.DEVNULL)
        # Atomic publication: link fails if a destination already exists; never overwrite it.
        os.link(temporary, destination)
        return destination
    finally:
        temporary.unlink(missing_ok=True)


@contextmanager
def restored_database(path):
    path = archive_path(path)
    container = None
    name = "storage-backup-check-" + secrets.token_hex(6)
    environment = dict(os.environ, POSTGRES_PASSWORD=secrets.token_urlsafe(32))
    try:
        container = run(["docker", "create", "--name", name,
            "--label", "storage-manager.purpose=backup-verification", "--network", "none",
            "--tmpfs", "/var/lib/postgresql/data:rw,nosuid,size=1g", "--memory", "1g",
            "--env", "POSTGRES_PASSWORD", "--env", "POSTGRES_USER=storage_restore",
            "--env", "POSTGRES_DB=storage_restore", "postgres:16"],
            "crear el contenedor temporal", env=environment).stdout.decode().strip()
        run(["docker", "start", container], "iniciar PostgreSQL temporal")
        deadline = time.monotonic() + 60
        while True:
            ready = subprocess.run(["docker", "exec", container, "pg_isready", "--host", "127.0.0.1",
                "--username", "storage_restore", "--dbname", "storage_restore"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0:
                break
            if time.monotonic() >= deadline:
                raise BackupError("PostgreSQL temporal no está listo. La base original no se ha modificado.")
            time.sleep(0.5)
        with path.open("rb") as source:
            run(["docker", "exec", "-i", container, "pg_restore", "--exit-on-error", "--no-owner",
                 "--no-privileges", "--username", "storage_restore", "--dbname", "storage_restore"],
                "restaurar la copia en el contenedor temporal", stdin=source)
        yield container
    finally:
        if container:
            try:
                run(["docker", "rm", "--force", container], "retirar el contenedor temporal")
            except BackupError:
                raise BackupError(f"No se pudo retirar el contenedor temporal {name}. La base original no se ha modificado.") from None


def restored_query(container, sql):
    return run(["docker", "exec", container, "psql", "--no-psqlrc", "--username", "storage_restore",
                "--dbname", "storage_restore", "--set", "ON_ERROR_STOP=1", "-At", "--command", sql],
               "comprobar los datos recuperados").stdout.decode().strip()


def verify_backup(path):
    with restored_database(path) as container:
        for table in TABLES:
            restored_query(container, f"SELECT count(*) FROM {table};")
        invalid = restored_query(container,
            "SELECT count(*) FROM pg_constraint WHERE contype='f' AND NOT convalidated;")
        if invalid != "0":
            raise BackupError("La copia contiene relaciones sin validar.")
    return len(TABLES)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    backup = commands.add_parser("backup", help="Crear una copia privada de un db ya iniciado")
    backup.add_argument("--project", required=True)
    backup.add_argument("--env-file", required=True, type=Path)
    backup.add_argument("--output-dir", type=Path, default=ROOT.parent / "backups")
    verify = commands.add_parser("verify", help="Restaurar y comprobar sin tocar la base original")
    verify.add_argument("archive", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "backup":
            print(backup_database(args.project, args.env_file, args.output_dir))
        else:
            tables = verify_backup(args.archive)
            print(f"Restauración verificada: {tables} tablas y relaciones. Contenedor temporal retirado.")
        return 0
    except (BackupError, OSError) as error:
        parser.exit(1, f"Error: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
