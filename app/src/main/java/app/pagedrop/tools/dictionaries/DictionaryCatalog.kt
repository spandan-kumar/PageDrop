package app.pagedrop.tools.dictionaries

data class DictionaryItem(
    val id: String,
    val name: String,
    val language: String,
    val description: String,
    val format: String, // "stardict", "dictd"
    val url: String,
    val license: String
)

object DictionaryCatalog {
    val items = listOf(
        DictionaryItem(
            id = "gcide",
            name = "GNU Collaborative Int'l Dictionary of English",
            language = "English",
            description = "Comprehensive English dictionary derived from Webster's Revised Unabridged.",
            format = "stardict",
            url = "https://downloads.sourceforge.net/project/goldendict/dictionaries/GCIDE/GCIDE.tar.bz2",
            license = "GPL"
        ),
        DictionaryItem(
            id = "wordnet",
            name = "WordNet 3.0",
            language = "English",
            description = "Lexical database with semantic relations between words.",
            format = "stardict",
            url = "https://downloads.sourceforge.net/project/goldendict/dictionaries/WordNet3/WordNet3.tar.bz2",
            license = "WordNet License"
        ),
        DictionaryItem(
            id = "websters_1913",
            name = "Webster's 1913 Unabridged",
            language = "English",
            description = "Classic reference dictionary, now in the public domain.",
            format = "stardict",
            url = "https://downloads.sourceforge.net/project/goldendict/dictionaries/Webster1913/Webster_1913.tar.bz2",
            license = "Public Domain"
        )
    )
}
