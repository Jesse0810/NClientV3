import com.maxwai.nclientv3.utility.BrowserUrls;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BrowserUrlChecks {
    private static int checks;
    private static final String ORIGIN = "https://nhentai.net";

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Map<String, String> parameters(String url) {
        Map<String, String> result = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query != null) for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                pair.length == 1 ? "" : URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    public static void main(String[] args) {
        String reported = "language%3A%22chinese%22+tag%3A%22incest%22+tag%3A%22mother%22";
        check((ORIGIN + "/search/?q=" + reported + "&page=1").equals(
            BrowserUrls.fromApiUrl(ORIGIN + "/api/v2/search?query=" + reported + "&page=1")),
            "Reported bookmark must open a website search with q");

        for (String raw : new String[]{
            "language:\"chinese\" tag:\"incest\" -tag:\"full color\"",
            "(artist:\"O'Brien\" OR title:\"中文 😀\") pages:>=24 uploaded:<=2w",
            "literal + & = ? # / % %20 %2B \"quoted\"",
            "", "-nclientv3"
        }) for (int page : new int[]{1, 19}) for (String sort : new String[]{null, "popular", "popular-week", "popular-month", "popular-today"}) {
            String api = ORIGIN + "/api/v2/search?query=" + URLEncoder.encode(raw, StandardCharsets.UTF_8)
                + "&page=" + page + (sort == null ? "" : "&sort=" + sort) + "&include=related";
            String web = BrowserUrls.fromApiUrl(api);
            check(web != null && URI.create(web).getPath().equals("/search/"), "Wrong website search path");
            Map<String, String> values = parameters(web);
            check(raw.equals(values.get("q")), "Canonical filters were changed or double-encoded");
            check(Integer.toString(page).equals(values.get("page")), "Page lost");
            check(sort == null ? !values.containsKey("sort") : sort.equals(values.get("sort")), "Sort lost");
            check(!values.containsKey("query") && !values.containsKey("include"), "API parameters leaked to website");
        }
        String rawRanges = BrowserUrls.fromApiUrl(ORIGIN + "/api/v2/search?query=pages:>=24+pages:<=80+uploaded:<=2w&page=4");
        check(parameters(rawRanges).get("q").equals("pages:>=24 pages:<=80 uploaded:<=2w"), "Form range operators changed");
        check((ORIGIN + "/?page=7").equals(BrowserUrls.fromApiUrl(ORIGIN + "/api/v2/galleries?page=7")), "Homepage route");
        check((ORIGIN + "/favorites/?q=word%2Bvalue&page=3").equals(BrowserUrls.fromApiUrl(
            ORIGIN + "/api/v2/favorites?page=3&q=word%2Bvalue")), "Favorites q or page lost");
        check((ORIGIN + "/favorites/?page=2").equals(BrowserUrls.fromApiUrl(ORIGIN + "/api/v2/favorites?page=2")), "Empty favorite route");
        check((ORIGIN + "/g/123/").equals(BrowserUrls.fromApiUrl(ORIGIN + "/api/v2/galleries/123?include=related,favorite")), "Gallery route");
        check((ORIGIN + "/random/").equals(BrowserUrls.fromApiUrl(ORIGIN + "/api/v2/galleries/random")), "Random route");
        check((ORIGIN + "/favorites/random").equals(BrowserUrls.fromApiUrl(ORIGIN + "/api/v2/favorites/random")), "Random favorite route");
        check("http://mirror.example:8080/search/?q=test&page=2".equals(BrowserUrls.fromApiUrl(
            "http://mirror.example:8080/api/v2/search?query=test&page=2")), "Mirror origin was changed");
        for (String invalid : new String[]{null, "", "not a URL", "file:/api/v2/galleries", ORIGIN + "/search/?q=test",
            ORIGIN + "/api/v2/unknown", ORIGIN + "/api/v2/search?query=%ZZ"})
            check(BrowserUrls.fromApiUrl(invalid) == null, "Unsupported request must not open an API page");
        System.out.println("PASS: " + checks + " browser URL checks (JVM, no website/device access)");
    }
}
