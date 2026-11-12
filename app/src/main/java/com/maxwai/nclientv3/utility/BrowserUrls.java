package com.maxwai.nclientv3.utility;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts internal API requests to website pages without changing the decoded filters. */
public final class BrowserUrls {
    private BrowserUrls() {}

    /** Returns null for an unsupported request instead of sending an API endpoint to a browser. */
    public static String fromApiUrl(String apiUrl) {
        if (apiUrl == null) return null;
        try {
            // URL also accepts the unescaped range operators produced by the search form.
            URL source = new URL(apiUrl);
            if ((!"https".equals(source.getProtocol()) && !"http".equals(source.getProtocol()))
                || source.getAuthority() == null || source.getAuthority().isEmpty()) return null;
            String path = source.getPath();
            Map<String, String> parameters = new LinkedHashMap<>();
            if (source.getQuery() != null) for (String part : source.getQuery().split("&")) {
                int separator = part.indexOf('=');
                String key = URLDecoder.decode(separator < 0 ? part : part.substring(0, separator), "UTF-8");
                String value = URLDecoder.decode(separator < 0 ? "" : part.substring(separator + 1), "UTF-8");
                if (!parameters.containsKey(key)) parameters.put(key, value);
            }
            StringBuilder result = new StringBuilder(source.getProtocol()).append("://").append(source.getAuthority());
            if ("/api/v2/search".equals(path)) {
                result.append("/search/");
                append(result, "q", parameters.getOrDefault("query", ""));
                append(result, "page", parameters.get("page"));
                append(result, "sort", parameters.get("sort"));
            } else if ("/api/v2/galleries".equals(path)) {
                result.append('/');
                append(result, "page", parameters.get("page"));
            } else if ("/api/v2/favorites".equals(path)) {
                result.append("/favorites/");
                append(result, "q", parameters.get("q"));
                append(result, "page", parameters.get("page"));
            } else if ("/api/v2/galleries/random".equals(path)) {
                result.append("/random/");
            } else if ("/api/v2/favorites/random".equals(path)) {
                result.append("/favorites/random");
            } else if (path.matches("/api/v2/galleries/[0-9]+")) {
                result.append("/g/").append(path.substring(path.lastIndexOf('/') + 1)).append('/');
            } else return null;
            return result.toString();
        } catch (MalformedURLException | UnsupportedEncodingException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void append(StringBuilder url, String key, String value) throws UnsupportedEncodingException {
        if (value == null) return;
        url.append(url.indexOf("?") < 0 ? '?' : '&').append(key).append('=')
            .append(URLEncoder.encode(value, "UTF-8"));
    }
}
