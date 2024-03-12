package eu.kanade.tachiyomi.extension.pt.huntersscans

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.concurrent.TimeUnit

class HuntersScans : ParsedHttpSource(), ConfigurableSource {
    override val name = "Hunters Scans"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val baseUrl = "https://huntersscan.xyz"

    override val versionId = 2

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    override val client: OkHttpClient =
        network.cloudflareClient.newBuilder()
            .setRandomUserAgent(
                preferences.getPrefUAType(),
                preferences.getPrefCustomUA(),
            )
            .rateLimitHost(baseUrl.toHttpUrl(), 1, 2, TimeUnit.SECONDS)
            .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    private fun fetchChapterList(url: HttpUrl, page: Int): List<SChapter> {
        return try {
            val mangaPaged = url.newBuilder()
                .addQueryParameter("page", "$page")
                .build()
            chapterListParseFromJS(client.newCall(GET(mangaPaged, headers)).execute())
        } catch (e: Exception) {
            Log.e("HuntersScans", e.toString())
            emptyList()
        }
    }

    private fun chapterListParseFromJS(response: Response): List<SChapter> {
        val jScript = response.asJsoup().select(chapterListSelector())
            .map { element -> element.html() }
            .filter { element -> element.isNotEmpty() }
            .first { chapterRegex.find(it) != null }

        val chaptersLinks = chapterRegex.findAll(jScript)
            .flatMap { result -> result.groups.mapNotNull { it?.value } }
            .toSet()

        return chaptersLinks.map { chapterLink ->
            SChapter.create().apply {
                name = chapterLink.toChapterName()
                setUrlWithoutDomain(chapterLink.toChapterAbsUrl())
            }
        }
    }

    private fun containsDuplicate(chapters: List<SChapter>): Boolean {
        return chapters.size != chapters.distinctBy { it.name }.size
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val alwaysVisibleChapters = mutableSetOf<SChapter>()

        val origin = response.request.url
        var currentPage = 1

        do {
            val chapterList = fetchChapterList(origin, currentPage)
            if (chapterList.size <= 2) {
                chapters += chapterList
                break
            }

            chapters += chapterList.sortedBy { it.name.toFloat() }
            alwaysVisibleChapters += chapters.removeFirst()
            alwaysVisibleChapters += chapters.removeLast()

            currentPage++
        }
        while (!containsDuplicate(chapters))

        chapters += alwaysVisibleChapters

        return chapters
            .distinctBy { it.name }
            .sortedBy { it.name.toFloat() }
            .reversed()
    }

    override fun chapterListSelector() = "script"

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesFromElement(element: Element) =
        SManga.create().apply {
            val type = element.selectFirst("span")!!.ownText().toCapitalize()
            title = "${element.selectFirst("h3")!!.ownText()} - $type"
            thumbnail_url = element.selectFirst("img")?.absUrl("src")
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response) =
        super.latestUpdatesParse(response).removeNovels()

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/ultimas-atualizacoes".toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = "main > div div:nth-child(2) > div.relative"

    override fun mangaDetailsParse(document: Document) =
        SManga.create().apply {
            val container = document.selectFirst("div.container")!!
            val type = container.selectFirst("ul > li:nth-child(1) p")!!.ownText().toCapitalize()
            title = "${container.selectFirst("h2")!!.ownText()} - $type"
            thumbnail_url = container.selectFirst("img")?.absUrl("src")
            genre = container.select("ul > li:nth-child(5) p").joinToString { it.ownText() }

            val statusLabel = container.selectFirst("ul > li:nth-child(3) p")?.ownText()
            status = when (statusLabel) {
                "ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            description = document.selectFirst("main > div > div")?.text()
        }

    override fun pageListParse(document: Document) =
        document.select("main.container img")
            .mapIndexed { i, element -> Page(i, imageUrl = element.absUrl("src")) }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val type = element.selectFirst("span")!!.ownText().toCapitalize()
        title = "${element.selectFirst("h2")!!.ownText()} - $type"
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = "li[aria-label='next page button']:not([aria-disabled])"

    override fun popularMangaParse(response: Response) =
        super.popularMangaParse(response).removeNovels()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "main > div a"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response) =
        super.searchMangaParse(response).removeNovels()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", "$page")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(slugPrefix)) {
            val mangaUrl = "/manga/${query.substringAfter(slugPrefix)}"
            return client.newCall(GET("$baseUrl$mangaUrl", headers))
                .asObservableSuccess().map { response ->
                    val manga = mangaDetailsParse(response).apply {
                        url = mangaUrl
                    }
                    MangasPage(listOf(manga), false)
                }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    private fun MangasPage.removeNovels(): MangasPage {
        return MangasPage(
            mangas = this.mangas.filter { !it.title.lowercase(Locale.ROOT).contains("novel") },
            hasNextPage = this.hasNextPage,
        )
    }

    private fun String.toCapitalize() =
        trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    private fun String.toChapterName(): String {
        return try {
            val matches = chapterNameRegex.find(trim())?.groupValues ?: emptyList()
            matches.last()
                .replace(" ", "")
                .replace("-", ".")
        } catch (e: Exception) { "0" }
    }

    private fun String.toChapterAbsUrl() = "$baseUrl${trim()}"

    companion object {
        val chapterRegex = """/ler/[\w+-]+-capitulo-[\d.-]+""".toRegex()
        val chapterNameRegex = """capitulo-([\d-.]+)""".toRegex()
        val slugPrefix = "slug:"
    }
}
