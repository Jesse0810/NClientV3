package com.maxwai.nclientv3.async;

import android.util.JsonReader;
import android.util.JsonToken;

import com.maxwai.nclientv3.utility.UpdateRelease;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Read a complete GitHub page, then select by version and matching published APK. */
public final class ReleaseFeed {
    private ReleaseFeed() {}

    public static UpdateRelease read(JsonReader reader, String flavor, boolean includePreview,
                                     String installedPackage) throws IOException {
        UpdateRelease best = null;
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue();
                continue;
            }
            best = UpdateRelease.newest(best, readRelease(reader, flavor, includePreview, installedPackage));
        }
        reader.endArray();
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Invalid release feed");
        return best;
    }

    private static UpdateRelease readRelease(JsonReader reader, String flavor, boolean includePreview,
                                             String installedPackage) throws IOException {
        String tag = null, body = null;
        boolean preview = false, draft = true;
        List<UpdateRelease.Asset> assets = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "tag_name": tag = readString(reader); break;
                case "body": body = readString(reader); break;
                case "prerelease":
                    if (reader.peek() == JsonToken.BOOLEAN) preview = reader.nextBoolean();
                    else reader.skipValue();
                    break;
                case "draft":
                    if (reader.peek() == JsonToken.BOOLEAN) draft = reader.nextBoolean();
                    else reader.skipValue();
                    break;
                case "assets":
                    if (reader.peek() != JsonToken.BEGIN_ARRAY) {
                        reader.skipValue();
                        break;
                    }
                    reader.beginArray();
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) assets.add(readAsset(reader));
                        else reader.skipValue();
                    }
                    reader.endArray();
                    break;
                default: reader.skipValue(); break;
            }
        }
        reader.endObject();
        return UpdateRelease.create(tag, body, preview, draft, assets, flavor, includePreview, installedPackage);
    }

    private static UpdateRelease.Asset readAsset(JsonReader reader) throws IOException {
        String name = null, url = null, state = null, digest = null;
        long size = -1;
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "name": name = readString(reader); break;
                case "browser_download_url": url = readString(reader); break;
                case "state": state = readString(reader); break;
                case "digest": digest = readString(reader); break;
                case "size":
                    if (reader.peek() == JsonToken.NUMBER) {
                        try { size = reader.nextLong(); }
                        catch (NumberFormatException invalid) { reader.skipValue(); }
                    } else reader.skipValue();
                    break;
                default: reader.skipValue(); break;
            }
        }
        reader.endObject();
        return new UpdateRelease.Asset(name, url, size, state, digest);
    }

    private static String readString(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.STRING) return reader.nextString();
        reader.skipValue();
        return null;
    }
}
