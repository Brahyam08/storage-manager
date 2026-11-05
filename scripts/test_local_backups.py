import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import local_backups as backups


class LocalBackupTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.environment = self.directory / ".env"
        self.environment.write_text("POSTGRES_DB=test\n")
        self.output = self.directory / "backups"

    def fake_dump(self, command, operation, **kwargs):
        if "sh" in command:
            kwargs["stdout"].write(b"PGDMP-test-fixture")
        return subprocess.CompletedProcess(command, 0, stdout=b"container-id\n")

    def test_private_backup_published_without_partial_files(self):
        with patch.object(backups, "run", side_effect=self.fake_dump):
            archive = backups.backup_database("test-project", self.environment, self.output)
            second = backups.backup_database("test-project", self.environment, self.output)
        self.assertNotEqual(archive, second)
        self.assertEqual(archive.read_bytes(), b"PGDMP-test-fixture")
        self.assertEqual(stat.S_IMODE(archive.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(self.output.stat().st_mode), 0o700)
        self.assertEqual(list(self.output.glob("*.partial")), [])

    def test_archive_validation_failure_leaves_no_backup(self):
        def failure(command, operation, **kwargs):
            if "pg_restore" in command:
                raise backups.BackupError("validation failed")
            return self.fake_dump(command, operation, **kwargs)
        with patch.object(backups, "run", side_effect=failure):
            with self.assertRaises(backups.BackupError):
                backups.backup_database("test-project", self.environment, self.output)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_existing_archive_is_never_overwritten(self):
        self.output.mkdir(mode=0o700)
        existing = self.output / "fixed.dump"
        existing.write_bytes(b"previous-backup")
        partial = self.output / "fixed.partial"
        descriptor = os.open(partial, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with patch.object(backups, "run", side_effect=self.fake_dump), patch.object(
                backups.tempfile, "mkstemp", return_value=(descriptor, str(partial))):
            with self.assertRaises(FileExistsError):
                backups.backup_database("test-project", self.environment, self.output)
        self.assertEqual(existing.read_bytes(), b"previous-backup")
        self.assertFalse(partial.exists())

    def test_repository_destination_is_rejected_before_docker(self):
        with patch.object(backups, "run") as docker:
            with self.assertRaises(backups.BackupError):
                backups.backup_database("test-project", self.environment, backups.ROOT / "backups")
            docker.assert_not_called()

    def test_missing_project_does_not_start_any_service(self):
        with patch.object(backups, "run", return_value=subprocess.CompletedProcess([], 0, stdout=b"")) as docker:
            with self.assertRaises(backups.BackupError):
                backups.backup_database("missing-project", self.environment, self.output)
        self.assertEqual(docker.call_count, 1)
        self.assertIn("ps", docker.call_args.args[0])
        self.assertFalse(self.output.exists())

    def test_public_directory_and_symlink_are_rejected(self):
        self.output.mkdir(mode=0o755)
        self.output.chmod(0o755)
        with patch.object(backups, "run", side_effect=self.fake_dump):
            with self.assertRaises(backups.BackupError):
                backups.backup_database("test-project", self.environment, self.output)
        link = self.directory / "link"
        link.symlink_to(self.output, target_is_directory=True)
        with patch.object(backups, "run") as docker:
            with self.assertRaises(backups.BackupError):
                backups.backup_database("test-project", self.environment, link)
            docker.assert_not_called()

    def test_invalid_archive_and_symlink_do_not_create_containers(self):
        archive = self.directory / "bad.dump"
        archive.write_text("not a database backup")
        with patch.object(backups, "run") as docker:
            with self.assertRaises(backups.BackupError):
                backups.verify_backup(archive)
            archive.write_bytes(b"PGDMP")
            link = self.directory / "linked.dump"
            link.symlink_to(archive)
            with self.assertRaises(backups.BackupError):
                backups.verify_backup(link)
            docker.assert_not_called()

    def test_failed_restore_removes_only_the_container_created_by_this_run(self):
        archive = self.directory / "fixture.dump"
        archive.write_bytes(b"PGDMP-test-fixture")
        calls = []
        def docker(command, operation, **kwargs):
            calls.append(command)
            if "pg_restore" in command:
                raise backups.BackupError("restore failed")
            return subprocess.CompletedProcess(command, 0, stdout=b"owned-id\n")
        with patch.object(backups, "run", side_effect=docker), patch.object(
                backups.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)):
            with self.assertRaises(backups.BackupError):
                backups.verify_backup(archive)
        self.assertEqual(calls[-1], ["docker", "rm", "--force", "owned-id"])

    def test_failed_container_creation_does_not_remove_an_existing_container(self):
        archive = self.directory / "fixture.dump"
        archive.write_bytes(b"PGDMP-test-fixture")
        with patch.object(backups, "run", side_effect=backups.BackupError("create failed")) as docker:
            with self.assertRaises(backups.BackupError):
                backups.verify_backup(archive)
        self.assertEqual(docker.call_count, 1)


if __name__ == "__main__":
    unittest.main()
