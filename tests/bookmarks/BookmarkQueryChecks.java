import android.os.Parcel;

import com.maxwai.nclientv3.utility.BookmarkFilters;
import com.maxwai.nclientv3.utility.BookmarkQuery;
import com.maxwai.nclientv3.api.components.Ranges;

public final class BookmarkQueryChecks {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void raw(String expression) {
        BookmarkFilters filters = BookmarkFilters.parse(expression);
        check(filters.rawExpression, "Ambiguous expression was decomposed: " + expression);
        check(filters.keywords.equals(expression) && filters.tags.isEmpty(), "Original expression changed");
        check(filters.minPages == -1 && filters.maxAge == -1 && filters.minFavorites == -1, "Fallback leaked partial ranges");
    }

    private static void checkFavoriteParcels(Ranges ranges) {
        Parcel parcel = Parcel.obtain();
        try {
            ranges.writeToParcel(parcel, 0);
            parcel.writeInt(1234);
            parcel.setDataPosition(0);
            Ranges restored = Ranges.CREATOR.createFromParcel(parcel);
            check(restored.toQuery().equals(ranges.toQuery()), "Favorites parcel round trip changed filters");
            check(parcel.readInt() == 1234, "New ranges parcel consumed a following value");
        } finally {
            parcel.recycle();
        }

        Parcel legacy = Parcel.obtain();
        try {
            legacy.writeInt(20);
            legacy.writeInt(50);
            legacy.writeInt(1);
            legacy.writeInt(2);
            legacy.writeInt(Ranges.TimeUnit.DAY.ordinal());
            legacy.writeInt(Ranges.TimeUnit.WEEK.ordinal());
            legacy.writeInt(1234);
            legacy.setDataPosition(0);
            Ranges restored = Ranges.CREATOR.createFromParcel(legacy);
            check(restored.toQuery().equals("pages:>=20 pages:<=50 uploaded:>=1d uploaded:<=2w"),
                "Legacy ranges parcel changed filters or invented a favorites minimum");
            check(legacy.readInt() == 1234, "Legacy ranges parcel consumed a following value");
        } finally {
            legacy.recycle();
        }
    }

    public static void main(String[] args) {
        String query = "my words  tag:\"incest\" -tag:\"full color\" language:chinese pages:>=50 pages:<=150 uploaded:>=1d uploaded:<=1w";
        BookmarkFilters filters = BookmarkFilters.parse(query);
        check(!filters.rawExpression, "Ordinary conjunction should be editable");
        check(filters.keywords.equals("my words"), "Keywords changed");
        check(filters.tags.size() == 3 && !filters.tags.get(0).excluded && filters.tags.get(1).excluded, "Include/exclude flags lost");
        check(filters.tags.get(1).value.equals("full color"), "Quoted multiword name split");
        check(filters.minPages == 50 && filters.maxPages == 150, "Page bounds changed");
        check(filters.minAge == 1 && filters.maxAge == 1 && filters.minAgeUnit.equals("d") && filters.maxAgeUnit.equals("w"), "Mixed units collapsed");

        for (String expression : new String[]{
            "(tag:\"incest\" OR tag:\"full color\")", "foo AND bar", "NOT tag:foo",
            "tag:a -tag:a", "tag:a tag:A", "pages:>=1 pages:>=2", "pages:5 pages:<=10",
            "pages:>5", "pages:>=20 pages:<=10", "-pages:20", "uploaded:2fortnights",
            "uploaded:>=48h uploaded:<=1d", "pages:999999999999999", "tag:\"unfinished",
            "tag:\"escaped\\\"name\"", "foo|bar"
        }) raw(expression);

        for (String expression : new String[]{query, " artist:\"桃子😀 OR 名字\" \t-tag:\"full color\" ", "(tag:a OR tag:b)", "unknown:\"keep exactly\"\n自由词"}) {
            StringBuilder restored = new StringBuilder();
            int position = 0;
            for (BookmarkQuery.Token token : BookmarkQuery.tokenize(expression)) {
                restored.append(expression, position, token.start).append(token.raw);
                position = token.end;
            }
            restored.append(expression, position, expression.length());
            check(restored.toString().equals(expression), "Display token spans changed original syntax");
        }
        BookmarkFilters unknown = BookmarkFilters.parse("custom:\"keep me\"  tag:incest");
        check(unknown.keywords.equals("custom:\"keep me\"") && unknown.tags.size() == 1, "Unknown predicate lost");
        check(BookmarkQuery.canEditAsTags("artist:\"OR 名字\""), "Literal OR mistaken for an operator");
        BookmarkFilters uppercase = BookmarkFilters.parse("PAGES:>=5 UPLOADED:<=2H");
        check(uppercase.minPages == 5 && uppercase.maxAgeUnit.equals("h"), "Uppercase fields not recognized");
        BookmarkFilters favorites = BookmarkFilters.parse("word FAVORITES:>=12 language:chinese");
        check(favorites.minFavorites == 12 && favorites.keywords.equals("word") && favorites.tags.size() == 1,
            "Favorites minimum could not be edited alongside keywords and tags");
        for (String expression : new String[]{"favorites:>12", "favorites:>=0", "-favorites:>=9", "favorites:>=999999999999"}) {
            BookmarkFilters unsupported = BookmarkFilters.parse(expression + " language:chinese");
            check(unsupported.keywords.equals(expression) && unsupported.minFavorites == -1 && unsupported.tags.size() == 1,
                "Unsupported favorites condition did not survive in keywords: " + expression);
        }
        raw("favorites:>=12 favorites:>=15");
        raw("favorites:>=12 OR language:chinese");

        Ranges ranges = new Ranges();
        check(ranges.getMinFavorites() == Ranges.UNDEFINED && ranges.isDefault(), "New ranges must be empty");
        ranges.setMinFavorites(12);
        check(!ranges.isDefault() && ranges.toQuery().equals("favorites:>=12"), "Minimum favorites alone was not serialized");
        ranges.setMinFavorites(0);
        check(ranges.isDefault() && ranges.toQuery().isEmpty(), "Zero did not clear the minimum favorites");
        ranges.setFromDate(1);
        ranges.setFromDateUnit(Ranges.TimeUnit.DAY);
        ranges.setToDate(1);
        ranges.setToDateUnit(Ranges.TimeUnit.WEEK);
        check(ranges.toQuery().equals("uploaded:>=1d uploaded:<=1w"), "Equal numbers with different units collapsed");
        ranges.setToDateUnit(Ranges.TimeUnit.DAY);
        check(ranges.toQuery().equals("uploaded:1d"), "True equality was not preserved");
        ranges.setFromPage(20);
        ranges.setMinFavorites(12);
        check(ranges.toQuery().equals("pages:>=20 uploaded:1d favorites:>=12"), "Favorites minimum did not compose with other ranges");
        checkFavoriteParcels(ranges);
        System.out.println("PASS: " + checks + " bookmark query/range checks (JVM logic, no Android device)");
    }
}
