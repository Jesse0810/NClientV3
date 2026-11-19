package com.maxwai.nclientv3.utility;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Published release identities, ordering and assets for this fork. */
public final class UpdateRelease {
    public static final String REPOSITORY_URL = "https://github.com/Jesse0810/NClientV3";
    public static final String API_URL = "https://api.github.com/repos/Jesse0810/NClientV3/releases";
    public static final String PACKAGE_NAME = "com.maxwai.nclientv3";
    private static final String DOWNLOAD_PREFIX = REPOSITORY_URL + "/releases/download/";

    public final String tag, body, assetName, downloadUrl, sha256;
    public final long assetSize;
    public final boolean preview, debugPackage;
    public final Version version;

    private UpdateRelease(String tag, String body, boolean preview, Version version, Asset asset) {
        this.tag = tag;
        this.body = body == null ? "" : body.trim();
        this.preview = preview;
        this.version = version;
        assetName = asset.name;
        downloadUrl = asset.url;
        assetSize = asset.size;
        sha256 = asset.digest == null ? null : asset.digest.substring("sha256:".length()).toLowerCase(Locale.ROOT);
        debugPackage = assetName.endsWith("_debug.apk");
    }

    public String pageUrl() {
        return REPOSITORY_URL + "/releases/tag/" + tag;
    }

    public boolean samePackage(String installedPackage) {
        return (PACKAGE_NAME + (debugPackage ? ".debug" : "")).equals(installedPackage);
    }

    public static UpdateRelease create(String tag, String body, boolean prerelease, boolean draft,
                                       List<Asset> assets, String flavor, boolean includePreview,
                                       String installedPackage) {
        Version version = Version.parse(tag);
        if (draft || version == null || (version.preview() && version.number == 0)) return null;
        boolean preview = prerelease || version.preview();
        if (preview && !includePreview) return null;
        Asset selected = null;
        int bestScore = -1;
        for (Asset asset : assets) {
            int score = assetScore(asset, tag, version, flavor, installedPackage);
            if (score > bestScore) {
                selected = asset;
                bestScore = score;
            }
        }
        return selected == null ? null : new UpdateRelease(tag, body, preview, version, selected);
    }

    private static int assetScore(Asset asset, String tag, Version version, String flavor, String installedPackage) {
        if (asset.name == null || asset.size <= 0 || !"uploaded".equals(asset.state)
            || !(DOWNLOAD_PREFIX + tag + "/" + asset.name).equals(asset.url)) return -1;
        if (asset.digest != null && !asset.digest.matches("sha256:[a-fA-F0-9]{64}")) return -1;
        String sdk;
        if ("post28".equals(flavor)) sdk = "android9plus";
        else if ("pre28".equals(flavor)) sdk = "android8";
        else return -1;
        String modern = "NClientV3_" + tag + "_" + sdk;
        boolean debug = asset.name.equals(modern + "_debug.apk");
        if (debug && !version.preview()) return -1;
        boolean modernMatch = debug || asset.name.equals(modern + ".apk");
        if (!modernMatch) {
            if (version.preview()) return -1;
            String base = tag.startsWith("v") ? tag.substring(1) : tag;
            String suffix = "pre28".equals(flavor) ? "_pre28" : "";
            if (!asset.name.equals("NClientV3_" + base + suffix + ".apk")
                && !asset.name.equals("NClientV3_" + base + "-release" + suffix + ".apk")) return -1;
        }
        boolean samePackage = (PACKAGE_NAME + (debug ? ".debug" : "")).equals(installedPackage);
        return (samePackage ? 10 : 0) + (modernMatch ? 1 : 0);
    }

    /** API creation order is unrelated to version order, including older backports. */
    public static UpdateRelease newest(UpdateRelease first, UpdateRelease second) {
        if (second == null) return first;
        if (first == null) return second;
        int comparison = second.version.compareTo(first.version);
        if (comparison > 0 || (comparison == 0 && first.preview && !second.preview)) return second;
        return first;
    }

    public boolean matchesVersionName(String versionName) {
        Version installed = Version.parseBuildName(versionName);
        if (installed == null) return false;
        // Older published Debug APKs omitted the preview sequence from versionName.
        if (debugPackage && versionName.endsWith("-debug") && !installed.preview())
            return version.sameBase(installed);
        return version.compareTo(installed) == 0;
    }

    /** Reject truncated responses, HTML/error payload sizes and mismatched GitHub digests. */
    public void copyApk(InputStream input, OutputStream output) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
        byte[] bytes = new byte[16384];
        long received = 0;
        int read;
        while ((read = input.read(bytes)) != -1) {
            if (read > assetSize - received) throw new IOException("Update exceeds published asset size");
            received += read;
            digest.update(bytes, 0, read);
            output.write(bytes, 0, read);
        }
        if (received != assetSize) throw new IOException("Incomplete update download");
        if (sha256 != null) {
            StringBuilder actual = new StringBuilder();
            for (byte value : digest.digest()) actual.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            if (!sha256.contentEquals(actual)) throw new IOException("Update SHA-256 mismatch");
        }
    }

    public static final class Asset {
        public final String name, url, state, digest;
        public final long size;

        public Asset(String name, String url, long size, String state, String digest) {
            this.name = name;
            this.url = url;
            this.size = size;
            this.state = state;
            this.digest = digest;
        }
    }

    public static final class Version implements Comparable<Version> {
        private static final Pattern TAG = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-(?:zh-preview\\.(\\d+)|(pre|rc)(\\d+)))?$");
        private final long major, minor, patch, number;
        private final int stage; // preview/pre < rc < final

        private Version(long major, long minor, long patch, int stage, long number) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.stage = stage;
            this.number = number;
        }

        public static Version parse(String text) {
            if (text == null) return null;
            Matcher match = TAG.matcher(text);
            if (!match.matches()) return null;
            try {
                int stage = match.group(4) != null || "pre".equals(match.group(5)) ? 0
                    : "rc".equals(match.group(5)) ? 1 : 2;
                String sequence = match.group(4) != null ? match.group(4) : match.group(6);
                return new Version(Long.parseLong(match.group(1)), Long.parseLong(match.group(2)),
                    Long.parseLong(match.group(3)), stage, sequence == null ? 0 : Long.parseLong(sequence));
            } catch (NumberFormatException invalid) {
                return null;
            }
        }

        public static Version parseBuildName(String name) {
            return name == null ? null : parse(name.replaceFirst("-(?:debug|release|relwithdebinfo)$", ""));
        }

        public static Version installed(String name, String releaseTag, boolean debug) {
            Version published = parse(releaseTag);
            if (published != null) return published;
            Version version = parseBuildName(name);
            if (version != null && debug && !version.preview())
                return new Version(version.major, version.minor, version.patch, 0, 0);
            return version;
        }

        public boolean preview() {
            return stage < 2;
        }

        private boolean sameBase(Version other) {
            return major == other.major && minor == other.minor && patch == other.patch;
        }

        @Override
        public int compareTo(Version other) {
            int comparison = Long.compare(major, other.major);
            if (comparison == 0) comparison = Long.compare(minor, other.minor);
            if (comparison == 0) comparison = Long.compare(patch, other.patch);
            if (comparison == 0) comparison = Integer.compare(stage, other.stage);
            if (comparison == 0) comparison = Long.compare(number, other.number);
            return comparison;
        }
    }
}
