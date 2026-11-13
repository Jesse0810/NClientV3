"""Check production APK selection and version comparison using the JDK, without Android/network."""

from pathlib import Path
import re
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main/java/com/maxwai/nclientv3/async/VersionChecker.java"


def java_block(source, marker):
    start = source.index(marker)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        if source[end] == "{":
            depth += 1
        elif source[end] == "}":
            depth -= 1
        end += 1
    return source[start:end]


def main():
    source = SOURCE.read_text(encoding="utf-8")
    assert 'https://api.github.com/repos/Jesse0810/NClientV3/releases"' in source
    assert 'https://github.com/Jesse0810/NClientV3/releases/latest"' in source
    prefix = re.search(r'private static final String RELEASE_DOWNLOAD_PREFIX = [^;]+;', source).group()
    java = r'''
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public final class ReleaseUpdateChecks {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] ignored) {
        String base = "https://github.com/Jesse0810/NClientV3/releases/download/v4.2.14/";
        String[] modern = {"NClientV3_v4.2.14_android9plus.apk", "NClientV3_v4.2.14_android8.apk"};
        String[] legacy = {"NClientV3_4.2.14.apk", "NClientV3_4.2.14_pre28.apk"};
        String[] alternate = {"NClientV3_4.2.14-release_android9plus.apk", "NClientV3_4.2.14-release_pre28.apk"};
        for (String[] names : new String[][] {modern, legacy, alternate}) {
            for (int index = 0; index < names.length; index++) {
                String url = base + names[index];
                check(isCompatibleApk(url, "post28") == (index == 0), url + " post28");
                check(isCompatibleApk(url, "pre28") == (index == 1), url + " pre28");
                check(!isCompatibleApk(url, "unknown"), "unknown flavor");
            }
        }
        String[] rejected = {
            null, "", base, base + "SHA256SUMS.txt", base + "mapping.txt", base + "app.aab",
            base + "NClientV3_v4.2.14_android8_debug.apk",
            base + "NClientV3_v4.2.14_android9plus_debug.apk",
            base + "NClientV3_v4.2.14-zh-preview.1_android9plus_debug.apk",
            base + "NClientV3_v4.2.13_android9plus.apk",
            base + "NClientV3_v4.2.14_android9plus.apk.sha256",
            base + "NClientV3_v4.2.14_android9plus.apk/extra",
            base + "nested/NClientV3_v4.2.14_android9plus.apk",
            base + "NClientV3_v4.2.14_android9plus.apk?download=1",
            base.replace("v4.2.14/", "v4.2.14-zh-preview.1/") + modern[0],
            base.replace("github.com/", "github.com.evil.example/") + modern[0],
            base.replace("Jesse0810/", "maxwai/") + modern[0],
            base.replace("https:", "http:") + modern[0]
        };
        for (String url : rejected) {
            check(!isCompatibleApk(url, "post28"), "reject post28: " + url);
            check(!isCompatibleApk(url, "pre28"), "reject pre28: " + url);
        }
        // API assets are unordered: checksums and the other SDK must be skipped.
        String[] assets = {base + "SHA256SUMS.txt", base + modern[1], base + modern[0]};
        for (String flavor : new String[] {"pre28", "post28"}) {
            String selected = null;
            for (String asset : assets) {
                if (isCompatibleApk(asset, flavor)) { selected = asset; break; }
            }
            check((base + modern[flavor.equals("pre28") ? 1 : 0]).equals(selected), "asset ordering " + flavor);
        }
        GitHubRelease release = new GitHubRelease();
        release.versionCode = "v4.2.14";
        check(!release.isNewerThenVersion("4.2.14-release"), "same stable version");
        check(release.isNewerThenVersion("4.2.13-release"), "new stable release");
        check(!release.isNewerThenVersion("4.2.15-release"), "no downgrade");
        check(release.isNewerThenVersion("4.2.14-rc1"), "release follows rc");
        System.out.println("PASS: " + assertions + " stable APK, SDK and version checks (production Java methods, no device)");
    }
'''
    java += prefix + "\n" + java_block(source, "private static boolean isCompatibleApk(") + "\n"
    java += java_block(source, "public static class GitHubRelease {") + "\n}\n"
    with tempfile.TemporaryDirectory(prefix="nclient-release-updates-") as temporary:
        directory = Path(temporary)
        fixture = directory / "ReleaseUpdateChecks.java"
        fixture.write_text(java, encoding="utf-8")
        subprocess.run(["javac", "--release", "11", "-encoding", "UTF-8", str(fixture)], check=True)
        subprocess.run(["java", "-cp", str(directory), "ReleaseUpdateChecks"], check=True)


if __name__ == "__main__":
    main()
