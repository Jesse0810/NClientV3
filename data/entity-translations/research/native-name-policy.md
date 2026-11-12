# Native-name source decisions (2026-11-12)

Frozen output: 10,406 artist names and 6,518 group names with source-backed display mappings; all 58,780 canonical names have a decision in native-records.json. Unmapped names remain byte-for-byte original. This is a partial original-name locator, not a claim that every artist/circle has been identified.

Inputs:

- EhTagTranslation/Database commit e0f66dc4424a53872473b05b7fb006ceebfcbe5d; artist/group exact namespace only. Existing license is CC BY-NC-SA 3.0 CN.
- Bangumi Archive 2026-11-10.210405Z original person name and explicit Japanese-name field, explicit roman field, and matching creator-profile URLs. Chinese-name fields never used as Japanese originals. Artist/group person types remain isolated. Licensed CC BY-SA 3.0; source citation and hashes are in native-manifest.json.
- Danbooru current public Artist metadata, 45,120 exact canonical lookups / 11,835 nondeleted records, snapshot 2026-11-12. Only name facts and source IDs/URLs are extracted; no images or wiki prose. A metadata open license was not found and no CC0/public-domain license is claimed. Retain attribution and the explicit license uncertainty. Every request and response hash is recorded in danbooru-source.json. Rate stayed at or below approximately one request per second.

Selection rules:

1. Community labels with Japanese kana and no known Chinese-specific or ambiguous Japanese glyph variants can supply community original names. They are labeled community_japanese_script_name, not claimed to be independently official-verified.
2. Pure Han labels are not accepted just because they look Japanese. Original orthography needs a Japanese article title, explicit original-name/readout field, clearly named creator-avatar alt, corroborated artist alias or Bangumi original-name evidence. Group links to a creator's wiki page cannot become group names.
3. Danbooru artist aliases must have either an exact native-name agreement with the Eh entry, or an overlap of profile IDs/URLs. Ambiguous multiple native aliases and announcement/status strings are rejected. Generic cross-site canonical-name agreement alone is insufficient; Artist records are not treated as Group records.
4. Official profile comparison preserves query identifiers such as circle_id and creater; otherwise different catalog records would collapse into false identities. Pixiv numeric user ID and X/Twitter username are normalized separately.
5. Bangumi can backfill an original name, or corroborate the same name. It does not replace an already-located native pen name with a person's newer primary name. Short/generic handles need profile/native-label corroboration, not just a roman-name collision. Explicit circle-native/circle-roman pairs stay in group namespace.
6. No OpenCC, Traditional/Simplified conversion or guessed romaji-to-kanji reconstruction is applied. Existing Japanese bytes, including original old glyphs supported by explicit evidence, are retained.
7. Known Western-name localization is rejected (gesundheit stays unchanged). A real Japanese pen name used in the Japanese manga industry, such as 新堂エル, can be retained regardless of citizenship; nationality alone is not a rule for rewriting or suppressing a native pen name. The community source does not supply a complete nationality registry, so this is original-name evidence rather than a guarantee of nationality for every entry.

Scripts: native_fetch.py, native_build.py and native_validate.py. Rebuilds require the source caches at their documented /tmp locations; the application build uses the frozen decisions in ../mapping-decisions.json.
