#!/usr/bin/env python3
"""Exercise production backup ZIP validation with JDK-only generated fixtures."""

from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.maxwai.nclientv3.async.database.export"
SOURCE = ROOT / "app/src/main/java" / PACKAGE.replace(".", "/")


def main():
    with tempfile.TemporaryDirectory(prefix="nclient-backup-checks-") as temporary:
        directory = Path(temporary)
        # Only the archive entry names are needed from the Android-facing exporter.
        exporter = directory / "Exporter.java"
        exporter.write_text(f"""package {PACKAGE};
final class Exporter {{
    static final String DB_ZIP_FILE = "Database.json";
    static final String[] SHARED_FILES = {{"Settings", "ScrapedTags"}};
}}
""", encoding="utf-8")
        classes = directory / "classes"
        subprocess.run([
            "javac", "--release", "11", "-encoding", "UTF-8", "-d", str(classes),
            str(exporter), str(SOURCE / "BackupArchive.java"), str(ROOT / "tests/backup/BackupArchiveChecks.java"),
        ], check=True)
        subprocess.run([
            "java", "-cp", str(classes), f"{PACKAGE}.BackupArchiveChecks", str(directory),
        ], check=True)


if __name__ == "__main__":
    main()
