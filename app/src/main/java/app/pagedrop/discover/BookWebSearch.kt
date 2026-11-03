package app.pagedrop.discover

/** Catalogs whose interactive search/download flow needs a browser. These are shortcuts, not book results. */
object BookWebSearch {
    /** Website shortcuts that exist only while the opt-in toggle is on. */
    internal val SHADOW_LIBRARY_NAMES = setOf("Anna’s Archive", "OceanofPDF")

    /**
     * [links] without the opt-in shortcuts unless [showShadowLibraries], and without shortcuts of language/region
     * groups that are off; used at render time so a toggle takes effect on cached results.
     */
    fun visibleLinks(links: List<BookSearchLink>, showShadowLibraries: Boolean, regions: Set<Region> = emptySet()): List<BookSearchLink> =
        links.filter { link ->
            (showShadowLibraries || link.sourceName !in SHADOW_LIBRARY_NAMES) &&
                Region.ofShortcut(link.sourceName)?.let { it in regions } ?: true
        }

    /**
     * Anna's Archive and OceanofPDF are listed only when [includeShadowLibraries] (Settings -> Book sources, off by default);
     * regional shortcuts only for groups in [regions].
     */
    fun links(
        query: String,
        failedSources: Set<SourceKind> = emptySet(),
        includeShadowLibraries: Boolean = false,
        regions: Set<Region> = emptySet(),
    ): List<BookSearchLink> {
        val q = TextUtil.enc(TextUtil.cleanQuery(query))
        if (q.isEmpty()) return emptyList()
        val fallbacks = failedSources.mapNotNull { kind ->
            when (kind) {
                SourceKind.GUTENBERG -> BookSearchLink("Project Gutenberg", "https://www.gutenberg.org/ebooks/search/?query=$q", "Search on the website while the catalog is unavailable")
                SourceKind.STANDARD_EBOOKS -> BookSearchLink("Standard Ebooks", "https://standardebooks.org/ebooks?query=$q", "Search on the website while the catalog is unavailable")
                SourceKind.INTERNET_ARCHIVE -> BookSearchLink("Internet Archive", "https://archive.org/search?query=$q", "Search on the website while the catalog is unavailable")
                SourceKind.DIGITAL_LIBRARY_INDIA -> BookSearchLink("Digital Library of India", "https://archive.org/search?query=${TextUtil.enc("collection:digitallibraryindia ($query)")}", "Search the Indian collection on Internet Archive")
                SourceKind.OPEN_LIBRARY -> BookSearchLink("Open Library", "https://openlibrary.org/search?q=$q", "Read or borrow on the website")
                SourceKind.HINDI_WIKISOURCE -> BookSearchLink("Hindi Wikisource", "https://hi.wikisource.org/w/index.php?title=Special:Search&search=$q", "Search Hindi texts on the website")
                SourceKind.HINDWI -> BookSearchLink("Hindwi", "https://www.hindwi.org/search/ebooks?q=$q", "Search Hindi literature on the website")
                SourceKind.REKHTA -> BookSearchLink("Rekhta", "https://www.rekhta.org/search/ebooks?q=$q", "Search Urdu literature on the website")
                SourceKind.ENGLISH_WIKISOURCE -> BookSearchLink("English Wikisource", "https://en.wikisource.org/w/index.php?title=Special:Search&search=$q", "Search English texts on the website")
                SourceKind.FADED_PAGE -> BookSearchLink("Faded Page", "https://www.fadedpage.com/csearch.php?title=$q", "Search English titles on Faded Page")
                SourceKind.MANYBOOKS -> BookSearchLink("ManyBooks", "https://manybooks.net/search-book?search=$q", "Search ebooks on ManyBooks; an account may be required")
                SourceKind.OPDS, SourceKind.WEBSITE -> null
            }
        }
        val shadow = includeShadowLibraries
        val brazilianOpenAccess = listOf(
            BookSearchLink("SciELO Brasil", "https://search.scielo.org/?q=$q", "Search Brazilian open-access scientific articles on the website"),
            BookSearchLink("Redalyc", "https://www.redalyc.org/busquedaArticuloFiltros.oa?q=$q", "Search Ibero-American open-access journals; includes Brazilian content"),
            BookSearchLink("BDTD", "https://bdtd.ibict.br/vufind/Search/Results?lookfor=$q&type=AllFields", "Search Brazilian theses and dissertations on the website"),
            BookSearchLink("Portal Domínio Público", "https://dominiopublico.mec.gov.br/", "Search for “${TextUtil.cleanQuery(query)}” on the website; Brazilian public-domain books and documents"),
            BookSearchLink("Biblioteca Brasiliana USP", "https://digital.bbm.usp.br/simple-search?query=$q", "Search the Brazilian digital collection; historical books and documents"),
            BookSearchLink("Lume (UFRGS)", "https://lume.ufrgs.br/discover?query=$q", "Search the UFRGS digital repository; theses, dissertations and articles"),
        )
        return (fallbacks + listOfNotNull(
            BookSearchLink("ePustakalay", "https://epustakalay.com/search_gcse/?q=$q", "Hindi and Indian-language books; search on the website"),
            if (shadow) BookSearchLink("Anna’s Archive", "https://annas-archive.gl/search?q=$q", "Shadow-library search; availability and download access vary") else null,
            BookSearchLink("Global Grey", "https://www.google.com/search?q=${TextUtil.enc("site:globalgreyebooks.com ${TextUtil.cleanQuery(query)}")}", "Search Global Grey’s English classics through its Google site search"),
            if (shadow) BookSearchLink("OceanofPDF", "https://oceanofpdf.com/", "Search for “${TextUtil.cleanQuery(query)}” on the website; automated access is blocked") else null,
        ) + brazilianOpenAccess).filter { link -> Region.ofShortcut(link.sourceName)?.let { it in regions } ?: true }
    }
}
