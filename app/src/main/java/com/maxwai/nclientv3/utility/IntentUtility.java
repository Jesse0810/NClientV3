package com.maxwai.nclientv3.utility;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import com.maxwai.nclientv3.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class IntentUtility extends Intent {

    public static void startAnotherActivity(Activity activity, Intent intent) {
        activity.runOnUiThread(() -> activity.startActivity(intent));
    }

    /** Explicit browser packages prevent verified website links from reopening this app. */
    public static void openBrowser(Activity activity, String url) {
        if (url == null) {
            Toast.makeText(activity, R.string.unable_to_open_browser, Toast.LENGTH_LONG).show();
            return;
        }
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager manager = activity.getPackageManager();
        Set<String> packages = new LinkedHashSet<>();
        for (ResolveInfo info : manager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (info.activityInfo != null
                && !activity.getPackageName().equals(info.activityInfo.packageName))
                packages.add(info.activityInfo.packageName);
        }
        List<Intent> browsers = new ArrayList<>();
        for (String name : packages)
            browsers.add(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(name));
        ResolveInfo preferred = manager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        try {
            if (preferred != null && preferred.activityInfo != null && packages.contains(preferred.activityInfo.packageName)) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(preferred.activityInfo.packageName));
            } else if (browsers.size() == 1) {
                activity.startActivity(browsers.get(0));
            } else if (!browsers.isEmpty()) {
                Intent chooser = Intent.createChooser(browsers.remove(0), activity.getString(R.string.open_in_browser));
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, browsers.toArray(new Intent[0]));
                activity.startActivity(chooser);
            } else {
                Toast.makeText(activity, R.string.unable_to_open_browser, Toast.LENGTH_LONG).show();
            }
        } catch (ActivityNotFoundException | SecurityException ignored) {
            Toast.makeText(activity, R.string.unable_to_open_browser, Toast.LENGTH_LONG).show();
        }
    }

}
