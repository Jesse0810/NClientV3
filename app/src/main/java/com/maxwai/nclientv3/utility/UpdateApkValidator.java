package com.maxwai.nclientv3.utility;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

/** Check actual APK identity before handing it to the system installer. */
public final class UpdateApkValidator {
    private UpdateApkValidator() {}

    @SuppressWarnings("deprecation")
    public static boolean validate(PackageManager manager, String installedPackage,
                                    File apk, UpdateRelease release) throws IOException {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed;
        try {
            installed = manager.getPackageInfo(installedPackage, flags);
        } catch (PackageManager.NameNotFoundException unavailable) {
            throw new IOException(unavailable);
        }
        if (candidate == null || !release.samePackage(installedPackage)
            || !installedPackage.equals(candidate.packageName) || !release.matchesVersionName(candidate.versionName))
            throw new IOException("Downloaded APK does not match the selected update");
        long candidateCode = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
        long installedCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
        if (candidateCode < installedCode) throw new IOException("Downloaded APK would downgrade the app");
        Signature[] current = Build.VERSION.SDK_INT >= 28
            ? installed.signingInfo == null ? null : installed.signingInfo.getApkContentsSigners() : installed.signatures;
        Signature[] incoming = Build.VERSION.SDK_INT >= 28
            ? candidate.signingInfo == null ? null : candidate.signingInfo.getApkContentsSigners() : candidate.signatures;
        return current != null && incoming != null && current.length > 0 && incoming.length > 0
            && new HashSet<>(Arrays.asList(current)).equals(new HashSet<>(Arrays.asList(incoming)));
    }
}
