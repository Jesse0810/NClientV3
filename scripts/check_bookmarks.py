"""Check saved-query preservation and the v13 bookmark migration without Android test dependencies."""

import json
import re
import sqlite3
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java/com/maxwai/nclientv3"


def database_checks():
    source = (JAVA / "async/database/Queries.java").read_text()
    table = source.split("public static class BookmarkTable {", 1)[1].split("public static class GalleryBridgeTable", 1)[0]
    expression = re.search(r'static final String CREATE_TABLE\s*=\s*((?:"(?:[^"\\]|\\.)*"|\s|\+)+);', table).group(1)
    schema = "".join(json.loads(value) for value in re.findall(r'"(?:[^"\\]|\\.)*"', expression))
    migration = re.search(r'db\.execSQL\("(ALTER TABLE Bookmark ADD COLUMN [^"]+)"\)',
                          (JAVA / "async/database/DatabaseHelper.java").read_text()).group(1)
    with sqlite3.connect(":memory:") as db:
        db.execute("CREATE TABLE Bookmark(url TEXT NOT NULL UNIQUE,page INT NOT NULL,type INT NOT NULL,tagId INT NOT NULL)")
        db.execute("INSERT INTO Bookmark VALUES(?,?,?,?)", ("saved-query", 9, 2, 314))
        before = db.execute("SELECT rowid,* FROM Bookmark").fetchall()
        db.execute(migration)
        assert db.execute("SELECT rowid,url,page,type,tagId FROM Bookmark").fetchall() == before
        assert db.execute("SELECT name FROM Bookmark").fetchone() == ("",)
        db.execute("UPDATE Bookmark SET name=? WHERE url=?", ("自定义书签 😀", "saved-query"))
        assert db.execute("SELECT rowid,url,page,type,tagId FROM Bookmark").fetchall() == before
        db.execute("INSERT INTO Bookmark VALUES(?,?,?,?,?)", ("other", 1, 2, 1, "Other"))
        snapshot = db.execute("SELECT rowid,* FROM Bookmark ORDER BY rowid").fetchall()
        try:
            db.execute("UPDATE Bookmark SET url=?,name=? WHERE url=?", ("other", "must not replace", "saved-query"))
        except sqlite3.IntegrityError:
            pass
        else:
            raise AssertionError("Duplicate URL unexpectedly replaced a bookmark")
        assert db.execute("SELECT rowid,* FROM Bookmark ORDER BY rowid").fetchall() == snapshot
        db.execute("INSERT INTO Bookmark(url,page,type,tagId) VALUES('old-backup',3,2,99)")
        assert db.execute("SELECT name FROM Bookmark WHERE url='old-backup'").fetchone() == ("",)
    with sqlite3.connect(":memory:") as db:
        db.execute(schema)
        columns = {row[1] for row in db.execute("PRAGMA table_info(Bookmark)")}
        assert {"url", "page", "type", "tagId", "name"} <= columns
        # The same guard used for upgrades from very old versions prevents a duplicate ALTER.
        if "name" not in columns:
            db.execute(migration)
    print("PASS: bookmark schema, legacy migration/import, rename and conflict preservation")


def query_checks():
    # Stubs supply only parcel/annotation signatures; tests execute the actual pure query logic.
    stubs = {
        "android/os/Parcelable.java": "package android.os; public interface Parcelable { int describeContents(); void writeToParcel(Parcel p,int f); interface Creator<T>{T createFromParcel(Parcel p);T[] newArray(int n);} }",
        "android/os/Parcel.java": "package android.os; public class Parcel { public int readInt(){throw new UnsupportedOperationException();} public void writeInt(int n){throw new UnsupportedOperationException();} }",
        "androidx/annotation/StringRes.java": "package androidx.annotation; public @interface StringRes {}",
        "com/maxwai/nclientv3/R.java": "package com.maxwai.nclientv3; public final class R { public static final class string {public static final int hours=1,days=2,weeks=3,months=4,years=5;} }",
    }
    with tempfile.TemporaryDirectory(prefix="nclient-bookmark-checks-") as temporary:
        directory = Path(temporary)
        files = []
        for relative, text in stubs.items():
            path = directory / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            files.append(str(path))
        files += [str(JAVA / "utility/BookmarkQuery.java"), str(JAVA / "utility/BookmarkFilters.java"),
                  str(JAVA / "api/components/Ranges.java"), str(ROOT / "tests/bookmarks/BookmarkQueryChecks.java")]
        subprocess.run(["javac", "--release", "11", "-d", str(directory / "classes"), *files], check=True)
        subprocess.run(["java", "-cp", str(directory / "classes"), "BookmarkQueryChecks"], check=True)


if __name__ == "__main__":
    database_checks()
    query_checks()
