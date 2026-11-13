import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.components.TagList;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.utility.BookmarkQuery;
import com.maxwai.nclientv3.utility.ContentTagAliasData;
import com.maxwai.nclientv3.utility.ContentTagAliases;
import com.maxwai.nclientv3.utility.ContentTagAliasQueries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Behavioral regressions: display grouping must not silently rewrite positive searches. */
public final class ContentAliasChecks {
    private static int checks;
    private static final Map<String, List<String>> EXPECTED = Map.of(
        "sole female", List.of("sole female", "sole femla", "sole fmelae"),
        "masturbation", List.of("masturbation", "masturbtion", "maturbation"),
        "transformation", List.of("transformation", "tranformation"),
        "soushuuhen", List.of("soushuuhen", "soshuuhen"),
        "tentacles", List.of("tentacles", "shokushu"),
        "watermarked", List.of("watermarked", "watermaked"),
        "multi-work series", List.of("multi-work series", "mult-work series")
    );

    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(Objects.equals(actual, expected), message + ": got " + actual + ", expected " + expected);
    }

    private static Tag tag(String name, int count, int id, TagStatus status) {
        return new Tag(name, count, id, TagType.TAG, status);
    }

    private static String term(String name, boolean excluded) {
        return (excluded ? "-" : "") + "tag:\"" + name + "\"";
    }

    private static List<String> contentTerms(String query, boolean excluded) {
        List<String> names = new ArrayList<>();
        for (BookmarkQuery.Token token : BookmarkQuery.tokenize(query))
            if ("tag".equals(token.namespace) && token.excluded == excluded) names.add(token.value);
        return names;
    }

    private static void groupsAndIdentity() {
        equal(ContentTagAliasData.groups(), EXPECTED, "reviewed groups must not broaden silently");
        for (Map.Entry<String, List<String>> group : EXPECTED.entrySet()) {
            String canonical = group.getKey();
            for (String name : group.getValue()) {
                equal(ContentTagAliases.canonicalName(name), canonical, "display group canonical name");
                equal(ContentTagAliases.members(name), group.getValue(), "all reviewed members retained");
                check(ContentTagAliases.sameTag(tag(name, 1, 1, TagStatus.DEFAULT),
                    tag(canonical, 1, 2, TagStatus.DEFAULT)), "aliases match despite different raw IDs");
                try {
                    ContentTagAliases.members(name).clear();
                    throw new AssertionError("mutable alias list");
                } catch (UnsupportedOperationException expected) { checks++; }
            }
        }
        for (String name : List.of("furry", "sample", "3d"))
            check(ContentTagAliases.sameTag(tag(name, 1, 1, TagStatus.DEFAULT),
                tag(name, 1, 2, TagStatus.DEFAULT)), "same content name with duplicate source IDs");
        check(!ContentTagAliases.sameTag(tag("group", 1, 1, TagStatus.DEFAULT),
            tag("orgy", 1, 1, TagStatus.DEFAULT)), "different concepts stay distinct even with a bad reused ID");
        Tag artist = new Tag("tranformation", 1, 20, TagType.ARTIST, TagStatus.DEFAULT);
        check(!ContentTagAliases.sameTag(artist, tag("transformation", 1, 21, TagStatus.DEFAULT)),
            "alias mapping must not cross namespaces");
        check(!ContentTagAliases.sameTag(artist,
            new Tag("tranformation", 1, 22, TagType.ARTIST, TagStatus.DEFAULT)), "other fields retain ID equality");
        equal(ContentTagAliases.canonicalName("unreviewed"), "unreviewed", "unknown name unchanged");
        equal(ContentTagAliases.members("unreviewed"), List.of("unreviewed"), "unknown singleton");
    }

    private static void displayMerging() {
        Tag alias = tag("tranformation", 30, 100, TagStatus.AVOIDED);
        Tag canonical = tag("transformation", 20, 200, TagStatus.ACCEPTED);
        Tag other = new Tag("tranformation", 8, 300, TagType.ARTIST, TagStatus.DEFAULT);
        Tag unrelated = tag("unreviewed", 9, 400, TagStatus.DEFAULT);
        for (List<Tag> input : List.of(List.of(alias, other, canonical, unrelated),
            List.of(canonical, other, alias, unrelated))) {
            List<Tag> result = ContentTagAliases.mergeTags(input);
            equal(result.size(), 3, "one display item for a reviewed family");
            Tag merged = result.get(0);
            equal(merged.getName(), "transformation", "canonical representative preferred");
            equal(merged.getId(), 200, "real canonical ID retained");
            equal(merged.getCount(), 30, "counts are not summed across overlapping sets");
            equal(merged.getStatus(), TagStatus.AVOIDED, "legacy exclusion wins a conflict");
            check(result.get(1) == other && result.get(2) == unrelated, "unrelated records and ordering unchanged");
            merged.setStatus(TagStatus.DEFAULT);
            equal(alias.getStatus(), TagStatus.AVOIDED, "merging must not mutate alias preference");
            equal(canonical.getStatus(), TagStatus.ACCEPTED, "merging must not mutate canonical preference");
        }
        Tag old = tag("sole fmelae", 3, 500, TagStatus.DEFAULT);
        Tag another = tag("sole femla", 2, 600, TagStatus.ACCEPTED);
        Tag withoutCanonical = ContentTagAliases.mergeTags(List.of(old, another)).get(0);
        check((withoutCanonical.getId() == 500 && withoutCanonical.getName().equals("sole fmelae"))
            || (withoutCanonical.getId() == 600 && withoutCanonical.getName().equals("sole femla")),
            "missing canonical row must not invent an ID/name pair");
        equal(withoutCanonical.getStatus(), TagStatus.ACCEPTED, "accepted outranks default");
        equal(ContentTagAliases.mergeTags(List.of(unrelated, unrelated, other, other)).size(), 4,
            "helper only collapses reviewed families");
    }

    private static void executionQueries() {
        for (List<String> family : EXPECTED.values()) {
            for (String name : family) {
                String positive = "  language:english\t" + term(name, false) + " pages:>=20  ";
                equal(ContentTagAliasQueries.expandExclusions(positive), positive,
                    "explicit standard or old positive spelling preserved byte for byte");
                String expanded = ContentTagAliasQueries.expandExclusions(term(name, true));
                equal(contentTerms(expanded, true), family, "exclusion covers every reviewed spelling once");
                check(contentTerms(expanded, false).isEmpty(), "exclusions cannot become inclusions");
                check(BookmarkQuery.canEditAsTags(expanded), "never generates unsupported OR/Boolean syntax");
            }
            List<String> repeated = new ArrayList<>();
            for (String name : family) repeated.add(term(name, true));
            repeated.add(term(family.get(0), true));
            String expanded = ContentTagAliasQueries.expandExclusions(String.join(" ", repeated));
            equal(contentTerms(expanded, true), family, "repeated family exclusions deduplicated");
            equal(contentTerms(ContentTagAliasQueries.expandExclusions(expanded), true), family,
                "re-expansion cannot multiply excluded terms");
        }
        String mixed = "tag:tranformation -tag:shokushu -artist:shokushu -tag:unknown";
        String expanded = ContentTagAliasQueries.expandExclusions(mixed);
        equal(contentTerms(expanded, false), List.of("tranformation"), "positive old term stays exact in mixed query");
        equal(contentTerms(expanded, true), List.of("tentacles", "shokushu", "unknown"),
            "only reviewed content exclusions expand");
        check(expanded.contains("-artist:shokushu"), "other namespaces remain verbatim");
        for (String opaque : Arrays.asList(null, "", "  \t", "-tag:unknown", "\"-tag:tranformation\"",
            "(-tag:tranformation)", "-tag:tranformation OR tag:tentacles",
            "-tag:tranformation|tag:tentacles", "tag:\"unterminated", "tag:\"escaped\\\"value\""))
            equal(ContentTagAliasQueries.expandExclusions(opaque), opaque, "opaque or unrelated query untouched");
    }

    private static void simpleTerms() {
        Tag positive = tag("tranformation", 1, 1, TagStatus.ACCEPTED);
        Tag excluded = tag("tranformation", 1, 1, TagStatus.AVOIDED);
        check(ContentTagAliasQueries.hasSimpleTerm("tag:tranformation", positive), "unquoted exact positive term");
        check(ContentTagAliasQueries.hasSimpleTerm("-TAG:\"tranformation\"", excluded), "namespace case and quoted exclusion");
        check(!ContentTagAliasQueries.hasSimpleTerm("-tag:tranformation", positive), "negative does not suppress positive");
        check(!ContentTagAliasQueries.hasSimpleTerm("tag:tranformation", excluded), "positive does not suppress negative");
        for (String different : Arrays.asList(null, "", "tag:transformation", "artist:tranformation",
            "tag:tranformation-extra", "\"tag:tranformation\"", "(tag:tranformation)",
            "tag:tranformation OR tag:tentacles"))
            check(!ContentTagAliasQueries.hasSimpleTerm(different, positive), "no fuzzy, alias, namespace or opaque suppression");
        Tag artist = new Tag("tranformation", 1, 7, TagType.ARTIST, TagStatus.DEFAULT);
        check(ContentTagAliasQueries.hasSimpleTerm("artist:tranformation", artist), "default status has positive polarity");
        Set<String> publicMethods = new HashSet<>();
        Arrays.stream(ContentTagAliases.class.getDeclaredMethods())
            .forEach(method -> publicMethods.add(method.getName()));
        check(!publicMethods.contains("canonicalizeQuery") && !publicMethods.contains("toQueryTag"),
            "display helper exposes no accidental positive-query rewrite API");
    }

    private static void exactGalleryMatching() {
        TagList gallery = new TagList();
        gallery.addTag(tag("furry", 1, 700, TagStatus.DEFAULT));
        gallery.addTag(tag("tranformation", 1, 701, TagStatus.DEFAULT));
        gallery.addTag(new Tag("shared", 1, 702, TagType.ARTIST, TagStatus.DEFAULT));
        check(gallery.hasTag(tag("furry", 1, 900, TagStatus.ACCEPTED)),
            "exact content name matches duplicate source IDs after management grouping");
        check(gallery.hasTag(tag("tranformation", 1, 901, TagStatus.ACCEPTED)),
            "explicit old spelling matches its exact content name");
        check(!gallery.hasTag(tag("transformation", 1, 902, TagStatus.ACCEPTED)),
            "positive standard spelling must not match a different old spelling");
        check(!gallery.hasTag(tag("unrelated", 1, 700, TagStatus.ACCEPTED)),
            "a reused content ID cannot bypass exact-name matching");
        check(gallery.hasTags(List.of(tag("furry", 1, 900, TagStatus.ACCEPTED),
            tag("tranformation", 1, 901, TagStatus.ACCEPTED))), "all exact include filters matched");
        check(!gallery.hasTags(List.of(tag("furry", 1, 900, TagStatus.ACCEPTED),
            tag("transformation", 1, 902, TagStatus.ACCEPTED))), "a missing exact include rejects the gallery");
        check(gallery.hasTag(new Tag("shared", 1, 702, TagType.ARTIST, TagStatus.ACCEPTED)),
            "other namespaces retain matching raw ID");
        check(!gallery.hasTag(new Tag("shared", 1, 999, TagType.ARTIST, TagStatus.ACCEPTED)),
            "other namespaces do not adopt content-name matching");
    }

    public static void main(String[] args) {
        groupsAndIdentity();
        displayMerging();
        executionQueries();
        simpleTerms();
        exactGalleryMatching();
        System.out.println("PASS: " + checks + " content-alias and exclusion checks (actual Java helpers; Tag/Android stubs, no device)");
    }
}
