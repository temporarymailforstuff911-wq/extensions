package com.kraptor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class HdFilmCehennemi : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/category/film-izle-2/" to "Filmler",
        "$mainUrl/yabancidiziizle-5/" to "Diziler",
        "$mainUrl/category/nette-ilk-filmler-1/" to "Nette İlk",
        "$mainUrl/category/tavsiye-filmler-izle2/" to "Tavsiye Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}page/$page/"
        val document = app.get(url).document
        val home = document.select("a.poster, a.mini-poster").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("strong.poster-title, h4.mini-poster-title")?.text()?.trim() ?: return null
        val href = fixUrl(this.attr("href"))
        val imgElement = this.selectFirst("img")
        val posterUrl = fixUrlNull(
            imgElement?.attr("data-src")?.ifEmpty { null }
                ?: imgElement?.attr("src")
        )

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        return document.select("a.poster, a.mini-poster").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.section-title")?.ownText()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: ""

        val posterImg = document.selectFirst("aside.post-info-poster img")
        val poster = fixUrlNull(
            posterImg?.attr("data-src")?.ifEmpty { null }
                ?: posterImg?.attr("src")
        )

        val description = document.selectFirst("article.post-info-content p, div.post-info-content p")?.text()?.trim()
        val year = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()

        val isTvSeries = url.contains("/dizi/")

        return if (isTvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.episode-item a, a.episode").forEach { ep ->
                val epHref = fixUrl(ep.attr("href"))
                val epTitle = ep.text().trim()
                episodes.add(newEpisode(epHref) {
                    this.name = epTitle
                })
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        document.select("div.video-container iframe, iframe.close, div.hdmv-play-container iframe").forEach { iframe ->
            val src = fixUrl(iframe.attr("data-src").ifEmpty { iframe.attr("src") })
            if (src.isNotEmpty()) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        return true
    }
}

@CloudstreamPlugin
class HdFilmCehennemiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HdFilmCehennemi())
    }
}
