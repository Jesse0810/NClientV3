"""Run actual update policy/feed/APK validation with JDK + Android API adapters, offline."""

from pathlib import Path
import base64
import itertools
import json
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java/com/maxwai/nclientv3"
TESTS = ROOT / "tests/updates"
REPOSITORY = "https://github.com/Jesse0810/NClientV3"

STUBS = {
    "android/util/JsonToken.java": """
package android.util;
public enum JsonToken { BEGIN_ARRAY, END_ARRAY, BEGIN_OBJECT, END_OBJECT, NAME, STRING, NUMBER, BOOLEAN, NULL, END_DOCUMENT }
""",
    "android/util/JsonReader.java": """
package android.util;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Token adapter: Python tokenizes JSON; production ReleaseFeed performs the stream reads. */
public final class JsonReader implements Closeable {
    private final List<String[]> events = new ArrayList<>();
    private int index;
    public JsonReader(Reader input) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        String line;
        while ((line = reader.readLine()) != null) {
            String[] event = line.split("\\t", -1);
            String value = event.length < 2 ? "" : new String(Base64.getDecoder().decode(event[1]), StandardCharsets.UTF_8);
            events.add(new String[] { event[0], value });
        }
        reader.close();
    }
    public JsonToken peek() { return index == events.size() ? JsonToken.END_DOCUMENT : JsonToken.valueOf(events.get(index)[0]); }
    private void consume(JsonToken expected) {
        if (peek() != expected) throw new IllegalStateException("Expected " + expected + ", got " + peek());
        index++;
    }
    private String value(JsonToken token) {
        if (peek() != token) throw new IllegalStateException("Expected " + token);
        return events.get(index++)[1];
    }
    public void beginArray() { consume(JsonToken.BEGIN_ARRAY); }
    public void endArray() { consume(JsonToken.END_ARRAY); }
    public void beginObject() { consume(JsonToken.BEGIN_OBJECT); }
    public void endObject() { consume(JsonToken.END_OBJECT); }
    public boolean hasNext() { return peek() != JsonToken.END_ARRAY && peek() != JsonToken.END_OBJECT && peek() != JsonToken.END_DOCUMENT; }
    public String nextName() { return value(JsonToken.NAME); }
    public String nextString() { return value(JsonToken.STRING); }
    public boolean nextBoolean() { return Boolean.parseBoolean(value(JsonToken.BOOLEAN)); }
    public long nextLong() {
        if (peek() != JsonToken.NUMBER) throw new IllegalStateException("Expected NUMBER");
        long result = Long.parseLong(events.get(index)[1]); index++; return result;
    }
    public void skipValue() {
        if (peek() == JsonToken.NAME) { index++; skipValue(); }
        else if (peek() == JsonToken.BEGIN_ARRAY) {
            beginArray(); while (hasNext()) skipValue(); endArray();
        } else if (peek() == JsonToken.BEGIN_OBJECT) {
            beginObject(); while (hasNext()) { nextName(); skipValue(); } endObject();
        } else if (peek() == JsonToken.END_DOCUMENT) throw new IllegalStateException("Unexpected end");
        else index++;
    }
    public void close() {}
}
""",
    "android/os/Build.java": """
package android.os;
public final class Build { public static final class VERSION { public static int SDK_INT = 36; } }
""",
    "android/content/pm/Signature.java": """
package android.content.pm;
public final class Signature {
    private final String certificate;
    public Signature(String certificate) { this.certificate = certificate; }
    public boolean equals(Object other) { return other instanceof Signature && certificate.equals(((Signature) other).certificate); }
    public int hashCode() { return certificate.hashCode(); }
}
""",
    "android/content/pm/SigningInfo.java": """
package android.content.pm;
public final class SigningInfo {
    private final Signature[] signatures;
    public SigningInfo(Signature[] signatures) { this.signatures = signatures; }
    public Signature[] getApkContentsSigners() { return signatures; }
}
""",
    "android/content/pm/PackageInfo.java": """
package android.content.pm;
public final class PackageInfo {
    public String packageName, versionName;
    public int versionCode;
    public long longCode;
    public Signature[] signatures;
    public SigningInfo signingInfo;
    public long getLongVersionCode() { return longCode != 0 ? longCode : versionCode; }
}
""",
    "android/content/pm/PackageManager.java": """
package android.content.pm;
public final class PackageManager {
    public static final int GET_SIGNING_CERTIFICATES = 0x08000000, GET_SIGNATURES = 0x40;
    public PackageInfo installed, archive;
    public int flags;
    public PackageInfo getPackageArchiveInfo(String path, int flags) { this.flags = flags; return archive; }
    public PackageInfo getPackageInfo(String name, int flags) throws NameNotFoundException {
        if (installed == null) throw new NameNotFoundException();
        return installed;
    }
    public static class NameNotFoundException extends Exception {}
}
""",
}


def asset(tag, sdk, debug=False, **changes):
    name = f"NClientV3_{tag}_{sdk}{'_debug' if debug else ''}.apk"
    value = dict(name=name, browser_download_url=f"{REPOSITORY}/releases/download/{tag}/{name}",
                 size=3, state="uploaded", digest=None)
    value.update(changes)
    return value


def release(tag, preview=False, draft=False, **changes):
    value = dict(tag_name=tag, body="更新说明（保留括号）", prerelease=preview, draft=draft,
                 assets=[asset(tag, "android8", preview), asset(tag, "android9plus", preview)])
    value.update(changes)
    return value


def fixtures():
    stable = release("v4.2.14")
    preview2 = release("v4.2.15-zh-preview.2", True)
    preview10 = release("v4.2.15-zh-preview.10", True)
    draft = release("v99.0.0", draft=True)
    cases = dict(current=json.loads((TESTS / "github-releases.json").read_text()),
                 mixed=[draft, preview2, stable, preview10],
                 empty=[], api_error={"message": "API rate limit exceeded"}, null_feed=None,
                 page1=[preview2], page2=[draft, stable, preview10], null_notes=[release("v4.2.15", body=None)])
    for index, order in enumerate(itertools.permutations([stable, preview10, release("v4.2.15")])):
        cases[f"order{index}"] = list(order)
    # Asset order, object field order, extra fields and nulls must not change selection.
    reordered = release("v4.2.16")
    reordered["assets"].insert(0, dict(name="SHA256SUMS.txt", size=198, state="uploaded",
                                     browser_download_url=f"{REPOSITORY}/releases/download/v4.2.16/SHA256SUMS.txt"))
    reordered = dict(reversed(list(reordered.items())))
    reordered["unknown"] = dict(nested=[None, 1, True, dict(ignore="value")])
    cases["reordered"] = [None, 17, reordered]
    broken = [
        release("v90.0.0", assets=[asset("v90.0.0", "android9plus", size=0)]),
        release("v91.0.0", assets=[asset("v91.0.0", "android9plus", state="starter")]),
        release("v92.0.0", assets=[asset("v92.0.0", "android9plus", size=1e99)]),
        release("v93.0.0", assets=[asset("v93.0.0", "android9plus", digest="sha512:bad")]),
        release("v94.0.0", assets=[asset("v1.0.0", "android9plus")]),
        release("v95.0.0", assets=[asset("v95.0.0", "android9plus", browser_download_url="https://example.com/file.apk")]),
        release("invalid", body=None, assets=None),
        dict(tag_name="v99.0.0", prerelease=False, assets=[]),  # missing draft, not trusted
    ]
    cases["broken"] = broken + [stable]
    cases["preview_only"] = [preview10, preview2]
    cases["missing_sdk"] = [release("v99.0.0", assets=[asset("v99.0.0", "android8")]), stable]
    return cases


def tokens(value):
    if isinstance(value, dict):
        yield ("BEGIN_OBJECT", "")
        for key, child in value.items():
            yield ("NAME", key)
            yield from tokens(child)
        yield ("END_OBJECT", "")
    elif isinstance(value, list):
        yield ("BEGIN_ARRAY", "")
        for child in value:
            yield from tokens(child)
        yield ("END_ARRAY", "")
    elif value is None:
        yield ("NULL", "")
    elif isinstance(value, bool):
        yield ("BOOLEAN", str(value).lower())
    elif isinstance(value, (int, float)):
        yield ("NUMBER", str(value))
    else:
        yield ("STRING", value)


def main():
    with tempfile.TemporaryDirectory(prefix="nclient-release-checks-") as temporary:
        directory = Path(temporary)
        sources = []
        for name, content in STUBS.items():
            path = directory / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            sources.append(path)
        data = directory / "fixtures"
        data.mkdir()
        for name, value in fixtures().items():
            (data / (name + ".events")).write_text("".join(
                kind + "\t" + base64.b64encode(text.encode()).decode() + "\n" for kind, text in tokens(value)
            ), encoding="utf-8")
        sources += [JAVA / "utility/UpdateRelease.java", JAVA / "async/ReleaseFeed.java",
                    JAVA / "utility/UpdateApkValidator.java", TESTS / "UpdateChecks.java"]
        subprocess.run(["javac", "--release", "11", "-encoding", "UTF-8", "-d", str(directory), *map(str, sources)], check=True)
        subprocess.run(["java", "-cp", str(directory), "UpdateChecks", str(data)], check=True)


if __name__ == "__main__":
    main()
