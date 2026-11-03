package app.pagedrop.discover

import java.util.Locale

/**
 * Language/region groups of sources (Settings -> Book sources -> Languages & regions). A group's built-in
 * sources are searched and its website shortcuts listed only while the group is on. Groups start off,
 * except those matching the phone's region or one of its languages.
 */
enum class Region(
    val title: String,
    val blurb: String,
    val countries: Set<String>,
    val languages: Set<String>,
    val sources: Set<SourceKind> = emptySet(),
    /** Website shortcut names, as in [BookSearchLink.sourceName]. */
    val shortcuts: Set<String> = emptySet(),
) {
    INDIA(
        "India · Hindi & Urdu",
        "Digital Library of India, Hindi Wikisource, Hindwi, Rekhta and ePustakalay",
        countries = setOf("IN"),
        languages = setOf("hi", "ur"),
        sources = setOf(SourceKind.DIGITAL_LIBRARY_INDIA, SourceKind.HINDI_WIKISOURCE, SourceKind.HINDWI, SourceKind.REKHTA),
        shortcuts = setOf("ePustakalay"),
    ),
    BRAZIL(
        "Brazil · Portuguese",
        "Open-access repositories: SciELO, Redalyc, BDTD, Domínio Público, Brasiliana USP and Lume",
        countries = setOf("BR"),
        languages = setOf("pt"),
        shortcuts = setOf("SciELO Brasil", "Redalyc", "BDTD", "Portal Domínio Público", "Biblioteca Brasiliana USP", "Lume (UFRGS)"),
    ),
    ;

    companion object {
        /** Groups that match [locales] by region or language; the default before the user changes anything. */
        fun defaultsFor(locales: List<Locale>): Set<Region> =
            entries.filter { r -> locales.any { it.country.uppercase() in r.countries || it.language.lowercase() in r.languages } }.toSet()

        /** The group a built-in source belongs to, or null for sources that are always available. */
        fun of(kind: SourceKind): Region? = entries.firstOrNull { kind in it.sources }

        fun ofShortcut(name: String): Region? = entries.firstOrNull { name in it.shortcuts }
    }
}
