package app.pagedrop.tools.fonts

data class FontItem(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val license: String,
    val targetDirectories: List<String> // stock Kindle, KOReader, or both
)

object FontCatalog {
    val items = listOf(
        FontItem(
            id = "atkinson_hyperlegible",
            name = "Atkinson Hyperlegible",
            description = "Designed for low vision readers with distinctive letterforms. Excellent legibility on e-ink.",
            url = "https://github.com/googlefonts/atkinson-hyperlegible/raw/main/fonts/ttf/AtkinsonHyperlegible-Regular.ttf",
            license = "OFL",
            targetDirectories = listOf("/mnt/us/fonts", "/mnt/us/koreader/fonts")
        ),
        FontItem(
            id = "literata",
            name = "Literata",
            description = "Google's Play Books typeface. Elegant serif with high readability.",
            url = "https://github.com/googlefonts/literata/raw/main/fonts/ttf/Literata-Regular.ttf",
            license = "OFL",
            targetDirectories = listOf("/mnt/us/fonts", "/mnt/us/koreader/fonts")
        ),
        FontItem(
            id = "chareink6",
            name = "ChareInk 6",
            description = "Weight-adjusted, high-contrast font tuned specifically for e-ink screens.",
            url = "https://github.com/sradc/ChareInk6/releases/download/v1.0/ChareInk6-Regular.ttf",
            license = "OFL",
            targetDirectories = listOf("/mnt/us/koreader/fonts")
        ),
        FontItem(
            id = "open_dyslexic",
            name = "OpenDyslexic",
            description = "Typeface designed against common symptoms of dyslexia.",
            url = "https://github.com/antijingoist/opendyslexic/raw/master/compiled/OpenDyslexic-Regular.otf",
            license = "OFL",
            targetDirectories = listOf("/mnt/us/fonts", "/mnt/us/koreader/fonts")
        ),
        FontItem(
            id = "bitter",
            name = "Bitter Pro",
            description = "Contemporary slab serif designed for comfortable reading on screens.",
            url = "https://github.com/googlefonts/bitter/raw/main/fonts/ttf/Bitter-Regular.ttf",
            license = "OFL",
            targetDirectories = listOf("/mnt/us/koreader/fonts")
        ),
        FontItem(
            id = "bookerly",
            name = "Bookerly (Note)",
            description = "Amazon's Kindle typeface. Cannot be distributed — will not install automatically.",
            url = "",
            license = "Proprietary (Amazon)",
            targetDirectories = emptyList()
        )
    )
}
