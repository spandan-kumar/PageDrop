package app.pagedrop.tools.screensavers

data class KindleModel(
    val name: String,
    val width: Int,
    val height: Int,
    val dpi: Int
)

object KindleModelRegistry {
    val models = listOf(
        KindleModel("Kindle Paperwhite 1 (2012)", 758, 1024, 212),
        KindleModel("Kindle Paperwhite 2 (2013)", 758, 1024, 212),
        KindleModel("Kindle Paperwhite 3 (2015)", 1072, 1448, 300),
        KindleModel("Kindle Paperwhite 4 (2018)", 1072, 1448, 300),
        KindleModel("Kindle Paperwhite 5 (2021)", 1236, 1648, 300),
        KindleModel("Kindle Basic (2014)", 600, 800, 167),
        KindleModel("Kindle Basic (2019)", 600, 800, 167),
        KindleModel("Kindle Basic (2022)", 1072, 1448, 300),
        KindleModel("Kindle Voyage (2014)", 1072, 1448, 300),
        KindleModel("Kindle Oasis 1 (2016)", 1072, 1448, 300),
        KindleModel("Kindle Oasis 2 (2017)", 1264, 1680, 300),
        KindleModel("Kindle Oasis 3 (2019)", 1264, 1680, 300),
        KindleModel("Kindle 4 (2011)", 600, 800, 167),
        KindleModel("Kindle Touch (2011)", 600, 800, 167),
        KindleModel("Kindle Keyboard (2010)", 600, 800, 167),
        KindleModel("Kindle DX (2009)", 824, 1200, 150),
    )

    fun defaultModel(): KindleModel = models[0]
}
