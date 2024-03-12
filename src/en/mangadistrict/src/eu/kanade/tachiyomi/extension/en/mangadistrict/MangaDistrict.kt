package eu.kanade.tachiyomi.extension.en.mangadistrict

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDistrict :
    Madara(
        "Manga District",
        "https://mangadistrict.com",
        "en",
    ),
    ConfigurableSource {

    override val mangaSubString = "read-scan"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaNextPageSelector() = "div[role=navigation] span.current + a.page"

    private val titleVersion = Regex("\\(.*\\)")

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            if (isRemoveTitleVersion()) {
                title = this.title.replace(titleVersion, "").trim()
            }
        }
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            if (isRemoveTitleVersion()) {
                title = this.title.replace(titleVersion, "").trim()
            }
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            if (isRemoveTitleVersion()) {
                title = this.title.replace(titleVersion, "").trim()
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)
        return when (getImgRes()) {
            IMG_RES_HIGH -> chapters.filterNot { it.url.contains("/v2-full-quality") }
            IMG_RES_FULL -> chapters.filterNot { it.url.contains("/v1-high-quality") }
            else -> chapters
        }
    }

    private fun isRemoveTitleVersion() = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private fun getImgRes() = preferences.getString(IMG_RES_PREF, IMG_RES_DEFAULT)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like “(Official)” or “(Doujinshi)” from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = IMG_RES_PREF
            title = "Image quality"
            entries = arrayOf("All", "High quality", "Full quality")
            entryValues = arrayOf(IMG_RES_ALL, IMG_RES_HIGH, IMG_RES_FULL)
            summary = "%s\nRefresh entry to update the chapter list."
            setDefaultValue(IMG_RES_DEFAULT)
        }.let(screen::addPreference)
    }

    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"

        private const val IMG_RES_PREF = "IMG_RES"
        private const val IMG_RES_ALL = "all"
        private const val IMG_RES_HIGH = "high"
        private const val IMG_RES_FULL = "full"
        private const val IMG_RES_DEFAULT = IMG_RES_ALL
    }
}
