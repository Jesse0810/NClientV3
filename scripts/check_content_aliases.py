"""Run content-alias and exclusion regressions with the JDK, without Android/network dependencies."""

import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java/com/maxwai/nclientv3"


def main():
    # Android parcel signatures and the Tag data model are stand-ins. The alias tables,
    # grouping/query helpers, token parser and tag enums are the production Java sources.
    stubs = {
        "android/os/Parcelable.java": """package android.os;
public interface Parcelable {
    int describeContents(); void writeToParcel(Parcel parcel, int flags);
    interface Creator<T> { T createFromParcel(Parcel parcel); T[] newArray(int size); }
}""",
        "android/os/Parcel.java": """package android.os;
public class Parcel {
    public byte readByte() { throw new UnsupportedOperationException(); }
    public String readString() { throw new UnsupportedOperationException(); }
    public void writeByte(byte value) { throw new UnsupportedOperationException(); }
    public void writeString(String value) { throw new UnsupportedOperationException(); }
    public <T> void readTypedList(java.util.List<T> values, Parcelable.Creator<T> creator) { throw new UnsupportedOperationException(); }
    public <T> void writeTypedList(java.util.List<T> values) { throw new UnsupportedOperationException(); }
}""",
        "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
        "com/maxwai/nclientv3/api/components/Tag.java": """package com.maxwai.nclientv3.api.components;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
public final class Tag {
    public static final android.os.Parcelable.Creator<Tag> CREATOR = new android.os.Parcelable.Creator<>() {
        public Tag createFromParcel(android.os.Parcel parcel) { throw new UnsupportedOperationException(); }
        public Tag[] newArray(int size) { return new Tag[size]; }
    };
    private final String name;
    private final int count, id;
    private final TagType type;
    private TagStatus status;
    public Tag(String name, int count, int id, TagType type, TagStatus status) {
        this.name = name; this.count = count; this.id = id; this.type = type; this.status = status;
    }
    public String getName() { return name; }
    public int getCount() { return count; }
    public int getId() { return id; }
    public TagType getType() { return type; }
    public String getTypeSingleName() { return type.getSingle(); }
    public TagStatus getStatus() { return status; }
    public void setStatus(TagStatus status) { this.status = status; }
    @Override public boolean equals(Object other) { return other instanceof Tag && id == ((Tag) other).id; }
    @Override public int hashCode() { return id; }
}""",
    }
    with tempfile.TemporaryDirectory(prefix="nclient-content-alias-checks-") as temporary:
        directory = Path(temporary)
        sources = []
        for relative, source in stubs.items():
            path = directory / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            sources.append(str(path))
        sources += [str(JAVA / relative) for relative in (
            "utility/ContentTagAliasData.java", "utility/ContentTagAliases.java",
            "utility/ContentTagAliasQueries.java", "utility/BookmarkQuery.java",
            "api/enums/TagType.java", "api/enums/TagStatus.java", "api/components/TagList.java",
        )]
        sources.append(str(ROOT / "tests/tags/ContentAliasChecks.java"))
        classes = str(directory / "classes")
        subprocess.run(["javac", "--release", "11", "-encoding", "UTF-8", "-d", classes, *sources], check=True)
        subprocess.run(["java", "-cp", classes, "ContentAliasChecks"], check=True)


if __name__ == "__main__":
    main()
