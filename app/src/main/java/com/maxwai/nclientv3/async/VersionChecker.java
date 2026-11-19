package com.maxwai.nclientv3.async;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.JsonReader;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.BuildConfig;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.UpdateApkValidator;
import com.maxwai.nclientv3.utility.UpdateRelease;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class VersionChecker {
    private static final AtomicBoolean CHECKING = new AtomicBoolean();
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean();
    private static final int MAX_PAGES = 20;
    private final AppCompatActivity context;
    private final boolean silent, includePreview;
    private final UpdateRelease.Version installed;

    public VersionChecker(AppCompatActivity context, boolean silent) {
        this.context = context;
        this.silent = silent;
        includePreview = Global.isEnableBeta();
        installed = UpdateRelease.Version.installed(BuildConfig.VERSION_NAME, BuildConfig.RELEASE_TAG,
            BuildConfig.APPLICATION_ID.endsWith(".debug"));
        if (!CHECKING.compareAndSet(false, true)) {
            if (!silent) toast(R.string.checking_update);
            return;
        }
        if (installed == null) {
            checkFailed(new IOException("Unknown installed version"));
            return;
        }
        fetchPage(1, null);
    }

    private void fetchPage(int page, UpdateRelease previousBest) {
        String url = UpdateRelease.API_URL + "?per_page=100&page=" + page;
        Global.getClient(context).newCall(new Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json").build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException error) {
                checkFailed(error);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                UpdateRelease best;
                boolean hasNext;
                try (Response result = response) {
                    if (!result.isSuccessful() || result.body() == null)
                        throw new IOException("Release API HTTP " + result.code());
                    try (JsonReader reader = new JsonReader(result.body().charStream())) {
                        best = UpdateRelease.newest(previousBest, ReleaseFeed.read(reader, BuildConfig.FLAVOR,
                            includePreview, context.getPackageName()));
                    }
                    hasNext = result.header("Link", "").contains("rel=\"next\"");
                    if (hasNext && page >= MAX_PAGES) throw new IOException("Incomplete release feed");
                } catch (IOException | RuntimeException error) {
                    checkFailed(error);
                    return;
                }
                if (hasNext) fetchPage(page + 1, best);
                else finishCheck(best);
            }
        });
    }

    private void finishCheck(UpdateRelease release) {
        CHECKING.set(false);
        onUi(() -> {
            if (release != null && release.version.compareTo(installed) > 0) createDialog(release);
            else if (!silent) toast(R.string.no_updates_found);
        });
    }

    private void checkFailed(Exception error) {
        CHECKING.set(false);
        LogUtility.e("Update check failed", error);
        if (!silent) toast(R.string.error_retrieving);
    }

    private void createDialog(UpdateRelease release) {
        boolean samePackage = release.samePackage(context.getPackageName());
        String notes = release.body.isEmpty() ? context.getString(R.string.update_no_release_notes) : release.body;
        String current = BuildConfig.RELEASE_TAG.isEmpty() ? BuildConfig.VERSION_NAME : BuildConfig.RELEASE_TAG;
        String message = context.getString(R.string.update_version_format, current, release.tag, notes);
        if (!samePackage) message = context.getString(R.string.update_separate_install_notice) + "\n\n" + message;
        new MaterialAlertDialogBuilder(context)
            .setTitle(release.preview ? R.string.new_beta_version_found : R.string.new_version_found)
            .setIcon(R.drawable.ic_file)
            .setMessage(message)
            .setPositiveButton(samePackage ? R.string.install : R.string.update_view_release, (dialog, which) -> {
                if (samePackage) downloadVersion(release);
                else openRelease(release);
            })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.github, (dialog, which) -> openRelease(release))
            .show();
    }

    private void openRelease(UpdateRelease release) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl())));
        } catch (ActivityNotFoundException unavailable) {
            toast(R.string.error_retrieving);
        }
    }

    private void downloadVersion(UpdateRelease release) {
        if (!DOWNLOADING.compareAndSet(false, true)) {
            toast(R.string.downloading_update);
            return;
        }
        toast(R.string.downloading_update);
        Global.getClient(context).newCall(new Request.Builder().url(release.downloadUrl).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException error) {
                downloadFailed(error);
                DOWNLOADING.set(false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                File partial = null;
                try (Response result = response) {
                    if (!result.isSuccessful() || result.body() == null)
                        throw new IOException("Update download HTTP " + result.code());
                    File folder = new File(context.getCacheDir(), "updates");
                    if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Update cache unavailable");
                    partial = File.createTempFile("update-", ".part.apk", folder);
                    try (FileOutputStream output = new FileOutputStream(partial);
                         InputStream input = result.body().byteStream()) {
                        release.copyApk(input, output);
                    }
                    if (!UpdateApkValidator.validate(context.getPackageManager(), context.getPackageName(), partial, release)) {
                        onUi(() -> showSignatureMismatch(release));
                        return;
                    }
                    File apk = new File(folder, release.assetName);
                    if (apk.exists() && !apk.delete()) throw new IOException("Cannot replace cached update");
                    if (!partial.renameTo(apk)) throw new IOException("Cannot finish update download");
                    partial = null;
                    onUi(() -> installApp(apk));
                } catch (IOException | RuntimeException error) {
                    downloadFailed(error);
                } finally {
                    if (partial != null && !partial.delete()) LogUtility.d("Could not remove partial update");
                    DOWNLOADING.set(false);
                }
            }
        });
    }

    private void showSignatureMismatch(UpdateRelease release) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_signature_mismatch_title)
            .setMessage(R.string.update_signature_mismatch_notice)
            .setPositiveButton(R.string.update_view_release, (dialog, which) -> openRelease(release))
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void downloadFailed(Exception error) {
        LogUtility.e("Update download failed", error);
        toast(R.string.download_update_failed);
    }

    private void installApp(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apk);
            @SuppressLint("RequestInstallPackagesPolicy") Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (IllegalArgumentException | ActivityNotFoundException | SecurityException unavailable) {
            toast(R.string.download_update_failed);
        }
    }

    private void onUi(Runnable action) {
        context.runOnUiThread(() -> {
            if (!context.isFinishing() && !context.isDestroyed()) action.run();
        });
    }

    private void toast(int resource) {
        onUi(() -> Toast.makeText(context, resource, Toast.LENGTH_SHORT).show());
    }
}
