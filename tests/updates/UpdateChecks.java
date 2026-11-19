import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.JsonReader;

import com.maxwai.nclientv3.async.ReleaseFeed;
import com.maxwai.nclientv3.utility.UpdateApkValidator;
import com.maxwai.nclientv3.utility.UpdateRelease;
import com.maxwai.nclientv3.utility.UpdateRelease.Asset;
import com.maxwai.nclientv3.utility.UpdateRelease.Version;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class UpdateChecks {
    private static int assertions;
    private static File fixtures;
    private static final String PKG = UpdateRelease.PACKAGE_NAME;
    private static final String DOWNLOAD = UpdateRelease.REPOSITORY_URL + "/releases/download/";

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private interface Operation { void run() throws Exception; }

    private static void failsIo(Operation action, String message) throws Exception {
        try { action.run(); throw new AssertionError("Expected IOException: " + message); }
        catch (IOException expected) { assertions++; }
    }

    private static Asset asset(String tag, String name) {
        return new Asset(name, DOWNLOAD + tag + "/" + name, 3, "uploaded", null);
    }

    private static UpdateRelease release(String tag, boolean preview, List<Asset> assets, String flavor, String pkg) {
        return UpdateRelease.create(tag, "notes", preview, false, assets, flavor, true, pkg);
    }

    private static UpdateRelease release(String tag, boolean preview) {
        String suffix = preview ? "_debug.apk" : ".apk";
        return release(tag, preview, Arrays.asList(asset(tag, "NClientV3_" + tag + "_android8" + suffix),
            asset(tag, "NClientV3_" + tag + "_android9plus" + suffix)), "post28", PKG);
    }

    private static UpdateRelease read(String name, String flavor, boolean preview, String pkg) throws Exception {
        try (JsonReader reader = new JsonReader(new FileReader(new File(fixtures, name + ".events")))) {
            return ReleaseFeed.read(reader, flavor, preview, pkg);
        }
    }

    private static void versions() {
        String[] ordered = {"v4.2.14", "v4.2.15-pre1", "v4.2.15-pre10", "v4.2.15-rc1",
            "v4.2.15-rc11", "v4.2.15", "v4.2.16-zh-preview.1", "v4.2.16-zh-preview.2",
            "v4.2.16-zh-preview.10", "v4.2.16", "v4.3.0", "v10.0.0"};
        for (int i = 0; i < ordered.length; i++) {
            for (int j = 0; j < ordered.length; j++) {
                check(Integer.signum(Version.parse(ordered[i]).compareTo(Version.parse(ordered[j])))
                    == Integer.signum(i - j), ordered[i] + " vs " + ordered[j]);
            }
        }
        for (String invalid : Arrays.asList(null, "", "v4.2", "v4.2.15junk", "text4.2.15", "4.2.15-debug-junk",
            "v4.2.15-zh-preview.-1", "v4.2.15-zh-preview.x", "v4.2.15-rc1oops", "v999999999999999999999999.2.15"))
            check(Version.parse(invalid) == null, "reject version: " + invalid);
        check(Version.installed("4.2.14-release", "", false).compareTo(Version.parse("v4.2.14")) == 0, "legacy stable");
        check(Version.installed("4.2.14-debug", "", true).compareTo(Version.parse("v4.2.14-zh-preview.1")) < 0,
            "legacy debug has unknown preview sequence");
        check(Version.installed("4.2.14-zh-preview.2-debug", "v4.2.14-zh-preview.2", true)
            .compareTo(Version.parse("v4.2.14-zh-preview.2")) == 0, "embedded preview identity");
        check(Version.installed("4.2.14-zh-preview.10-debug", "", true)
            .compareTo(Version.parse("v4.2.14-zh-preview.2")) > 0, "preview identity fallback");
        check(Version.installed("4.2.14-release", "invalid", false) != null, "invalid metadata fallback");
        check(Version.installed("unknown", "", false) == null, "invalid build identity");
    }

    private static void assets() {
        String tag = "v4.2.15";
        Asset android9 = asset(tag, "NClientV3_" + tag + "_android9plus.apk");
        Asset android8 = asset(tag, "NClientV3_" + tag + "_android8.apk");
        List<Asset> unordered = Arrays.asList(asset(tag, "SHA256SUMS.txt"), android8, android9);
        check(release(tag, false, unordered, "post28", PKG).assetName.equals(android9.name), "modern android9");
        check(release(tag, false, unordered, "pre28", PKG).assetName.equals(android8.name), "modern android8");
        check(release(tag, false, unordered, "unknown", PKG) == null, "unknown SDK");
        for (String suffix : Arrays.asList("", "-release")) {
            List<Asset> legacy = Arrays.asList(asset(tag, "NClientV3_4.2.15" + suffix + "_pre28.apk"),
                asset(tag, "NClientV3_4.2.15" + suffix + ".apk"));
            check(release(tag, false, legacy, "post28", PKG).assetName.endsWith(suffix + ".apk"), "legacy android9");
            check(release(tag, false, legacy, "pre28", PKG).assetName.endsWith("_pre28.apk"), "legacy android8");
        }
        Asset[] invalid = {
            asset(tag, "NClientV3_" + tag + "_android9plus_debug.apk"),
            asset(tag, "NClientV3_" + tag + "_android9plus_unsigned.apk"),
            asset("v4.2.14", "NClientV3_v4.2.14_android9plus.apk"),
            new Asset(android9.name, android9.url + "?download=1", 3, "uploaded", null),
            new Asset(android9.name, android9.url.replace("github.com", "github.com.example"), 3, "uploaded", null),
            new Asset(android9.name, android9.url.replace("Jesse0810", "maxwai"), 3, "uploaded", null),
            new Asset(android9.name, android9.url.replace("https:", "http:"), 3, "uploaded", null),
            new Asset(android9.name, android9.url, 0, "uploaded", null),
            new Asset(android9.name, android9.url, -1, "uploaded", null),
            new Asset(android9.name, android9.url, 3, "starter", null),
            new Asset(android9.name, android9.url, 3, "uploaded", "sha256:bad"),
            new Asset(null, null, -1, null, null),
        };
        for (Asset bad : invalid) {
            check(release(tag, false, Collections.singletonList(bad), "post28", PKG) == null, "invalid asset rejected");
            check(release(tag, false, Arrays.asList(bad, android9), "post28", PKG) != null, "invalid asset skipped");
        }
        check(UpdateRelease.create(tag, null, false, true, unordered, "post28", true, PKG) == null, "draft rejected");
        String preview = "v4.2.16-zh-preview.11";
        UpdateRelease test = release(preview, true);
        check(test.preview && !test.samePackage(PKG) && test.samePackage(PKG + ".debug"), "Debug preview is separate app");
        check(test.pageUrl().equals(UpdateRelease.REPOSITORY_URL + "/releases/tag/" + preview), "exact preview page");
        check(test.matchesVersionName("4.2.16-zh-preview.11-debug"), "matches preview sequence");
        check(!test.matchesVersionName("4.2.16-zh-preview.2-debug"), "reject wrong preview APK sequence");
        check(test.matchesVersionName("4.2.16-debug"), "legacy preview APK base accepted");
        check(!test.matchesVersionName("4.2.15-debug"), "reject wrong preview base");
        check(UpdateRelease.create(preview, null, true, false,
            Collections.singletonList(asset(preview, test.assetName)), "post28", false, PKG) == null, "preview switch off");
        List<Asset> previewVariants = Arrays.asList(asset(preview, "NClientV3_" + preview + "_android9plus_debug.apk"),
            asset(preview, "NClientV3_" + preview + "_android9plus.apk"));
        check(release(preview, true, previewVariants, "post28", PKG).samePackage(PKG), "prefer same-package release preview");
        check(release(preview, true, previewVariants, "post28", PKG + ".debug").samePackage(PKG + ".debug"),
            "prefer same-package Debug preview");
    }

    private static void feeds() throws Exception {
        for (String flavor : Arrays.asList("post28", "pre28")) {
            String sdk = flavor.equals("post28") ? "android9plus" : "android8";
            for (boolean enabled : new boolean[] {false, true}) {
                UpdateRelease current = read("current", flavor, enabled, PKG);
                check(current.tag.equals("v4.2.14") && !current.preview, "actual GitHub final beats same-version preview");
                check(current.assetName.equals("NClientV3_v4.2.14_" + sdk + ".apk"), "actual GitHub APK matches SDK");
                UpdateRelease mixed = read("mixed", flavor, enabled, PKG);
                check(mixed.tag.equals(enabled ? "v4.2.15-zh-preview.10" : "v4.2.14"), "toggle controls previews");
                check(mixed.assetName.contains(sdk), "mixed APK matches SDK");
                for (int order = 0; order < 6; order++)
                    check(read("order" + order, flavor, enabled, PKG).tag.equals("v4.2.15"), "order independent final promotion");
            }
        }
        check(read("broken", "post28", true, PKG).tag.equals("v4.2.14"), "broken metadata/asset records skipped");
        check(read("missing_sdk", "post28", true, PKG).tag.equals("v4.2.14"), "release without matching SDK skipped");
        check(read("missing_sdk", "pre28", true, PKG).tag.equals("v99.0.0"), "other SDK has its matching release");
        check(read("preview_only", "post28", false, PKG) == null, "no previews when disabled");
        check(read("preview_only", "post28", true, PKG).tag.equals("v4.2.15-zh-preview.10"), "multi-digit preview sorted");
        check(read("null_notes", "post28", true, PKG).body.isEmpty(), "missing notes do not hide update");
        check(read("reordered", "post28", true, PKG).tag.equals("v4.2.16"), "field/asset order and unknown nested fields");
        check(read("reordered", "post28", true, PKG).body.contains("（保留括号）"), "notes preserve parentheses");
        check(read("empty", "post28", true, PKG) == null, "empty feed");
        UpdateRelease paged = UpdateRelease.newest(read("page1", "post28", true, PKG), read("page2", "post28", true, PKG));
        check(paged.tag.equals("v4.2.15-zh-preview.10"), "maximum across pages");
        paged = UpdateRelease.newest(read("page1", "post28", false, PKG), read("page2", "post28", false, PKG));
        check(paged.tag.equals("v4.2.14"), "stable can be on later page");
        for (String invalid : Arrays.asList("api_error", "null_feed")) {
            try { read(invalid, "post28", true, PKG); throw new AssertionError("Invalid API response accepted"); }
            catch (IllegalStateException expected) { assertions++; }
        }
    }

    private static void downloads() throws Exception {
        String tag = "v4.2.15";
        String name = "NClientV3_" + tag + "_android9plus.apk";
        Asset digest = new Asset(name, DOWNLOAD + tag + "/" + name, 3, "uploaded",
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        UpdateRelease update = release(tag, false, Collections.singletonList(digest), "post28", PKG);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        update.copyApk(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), output);
        check(Arrays.equals(output.toByteArray(), "abc".getBytes(StandardCharsets.UTF_8)), "verified download copied");
        for (String bad : Arrays.asList("", "ab", "abcd", "xyz", "<html>error</html>"))
            failsIo(() -> update.copyApk(new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream()), "incomplete/oversized/wrong-digest download " + bad);
        UpdateRelease noDigest = release(tag, false);
        noDigest.copyApk(new ByteArrayInputStream(new byte[] {1, 2, 3}), new ByteArrayOutputStream());
        check(noDigest.sha256 == null, "legacy release without API digest still checks length and APK later");
        failsIo(() -> noDigest.copyApk(new ByteArrayInputStream(new byte[] {1}), new ByteArrayOutputStream()), "legacy truncated");
    }

    private static PackageInfo info(String pkg, String version, int code, String... certificates) {
        PackageInfo info = new PackageInfo();
        info.packageName = pkg;
        info.versionName = version;
        info.versionCode = code;
        info.signatures = Arrays.stream(certificates).map(Signature::new).toArray(Signature[]::new);
        info.signingInfo = new SigningInfo(info.signatures);
        return info;
    }

    private static void apkValidation() throws Exception {
        UpdateRelease update = release("v4.2.15", false);
        File archive = new File("update.part");
        for (int sdk : new int[] {26, 27, 28, 36}) {
            Build.VERSION.SDK_INT = sdk;
            PackageManager manager = new PackageManager();
            manager.installed = info(PKG, "4.2.14-release", 421400, "fixed-cert");
            manager.archive = info(PKG, "4.2.15-release", 421500, "fixed-cert");
            check(UpdateApkValidator.validate(manager, PKG, archive, update), "fixed signing key update SDK " + sdk);
            check(manager.flags == (sdk >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES), "signature API by SDK");
            manager.installed.versionCode = 421500;
            check(UpdateApkValidator.validate(manager, PKG, archive, update), "equal versionCode is allowed");
            manager.installed.versionCode = 421400;
            manager.archive = info(PKG, "4.2.15-release", 421500, "new-temporary-cert");
            check(!UpdateApkValidator.validate(manager, PKG, archive, update), "signature mismatch requires manual installation");
            manager.archive = info(PKG, "4.2.15-release", 421500);
            check(!UpdateApkValidator.validate(manager, PKG, archive, update), "missing signature rejected");
            manager.archive = info(PKG + ".debug", "4.2.15-release", 421500, "fixed-cert");
            failsIo(() -> UpdateApkValidator.validate(manager, PKG, archive, update), "wrong actual package");
            manager.archive = info(PKG, "4.2.14-release", 421400, "fixed-cert");
            failsIo(() -> UpdateApkValidator.validate(manager, PKG, archive, update), "wrong actual APK version");
            manager.archive = info(PKG, "4.2.15-release", 421399, "fixed-cert");
            failsIo(() -> UpdateApkValidator.validate(manager, PKG, archive, update), "versionCode downgrade");
            manager.archive = null;
            failsIo(() -> UpdateApkValidator.validate(manager, PKG, archive, update), "not an APK archive");
            manager.archive = info(PKG, "4.2.15-release", 421500, "fixed-cert");
            failsIo(() -> UpdateApkValidator.validate(manager, PKG, archive, release("v4.2.15-zh-preview.1", true)), "cross-package preview");
            manager.installed = info(PKG, "4.2.14-release", 421400, "a", "b");
            manager.archive = info(PKG, "4.2.15-release", 421500, "b", "a");
            check(UpdateApkValidator.validate(manager, PKG, archive, update), "signer set ordering");
            manager.installed = null;
            failsIo(() -> UpdateApkValidator.validate(manager, PKG, archive, update), "installed package unavailable");
            String debug = PKG + ".debug";
            manager.installed = info(debug, "4.2.15-zh-preview.2-debug", 421500, "fixed-debug-cert");
            manager.archive = info(debug, "4.2.15-zh-preview.10-debug", 421500, "fixed-debug-cert");
            check(UpdateApkValidator.validate(manager, debug, archive, release("v4.2.15-zh-preview.10", true)),
                "same-base preview update with equal versionCode");
            manager.archive.versionName = "4.2.15-zh-preview.2-debug";
            failsIo(() -> UpdateApkValidator.validate(manager, debug, archive, release("v4.2.15-zh-preview.10", true)),
                "wrong actual preview sequence");
            if (sdk >= 28) {
                manager.installed = info(PKG, "4.2.14-release", 1, "fixed-cert");
                manager.archive = info(PKG, "4.2.15-release", 2, "fixed-cert");
                manager.installed.longCode = (1L << 33) + 5;
                manager.archive.longCode = (1L << 33) + 4;
                failsIo(() -> UpdateApkValidator.validate(manager, PKG, archive, update), "long versionCode downgrade");
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        fixtures = new File(arguments[0]);
        versions(); assets(); feeds(); downloads(); apkValidation();
        System.out.println("PASS: " + assertions + " release/preview/feed/download/APK checks (production Java; Android token/PM adapters, no device)");
    }
}
