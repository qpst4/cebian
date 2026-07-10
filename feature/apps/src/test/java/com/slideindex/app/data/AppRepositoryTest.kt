package com.slideindex.app.data

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import com.slideindex.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppRepositoryTest {

    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        repository = AppRepository(
            context = RuntimeEnvironment.getApplication(),
            appLaunchPort = object : AppLaunchPort {
                override fun launch(intent: Intent, settings: AppSettings, fullscreen: Boolean) = Unit
            },
        )
    }

    @Test
    fun groupedItems_insertsHeadersByLetter() {
        val apps = listOf(
            app("com.b.app", "Beta", 'B'),
            app("com.a.app", "Alpha", 'A'),
            app("com.a.two", "Another", 'A'),
        )
        val items = repository.groupedItems(apps)
        val alpha = apps.first { it.label == "Alpha" }
        val another = apps.first { it.label == "Another" }
        val beta = apps.first { it.label == "Beta" }
        assertEquals(
            listOf(
                AppListItem.Header('A'),
                AppListItem.App(alpha),
                AppListItem.App(another),
                AppListItem.Header('B'),
                AppListItem.App(beta),
            ),
            items,
        )
    }

    @Test
    fun searchApps_matchesLabelPackageAndPinyinKey() {
        val apps = listOf(
            app("com.tencent.mm", "微信", 'W'),
            app("com.android.chrome", "Chrome", 'C'),
        )
        val byLabel = repository.searchApps(apps, "chrome")
        assertEquals(listOf(apps[1]), byLabel)

        val byPackage = repository.searchApps(apps, "tencent")
        assertEquals(listOf(apps[0]), byPackage)
    }

    @Test
    fun searchApps_emptyQueryReturnsOriginalList() {
        val apps = listOf(app("com.a", "A", 'A'))
        assertEquals(apps, repository.searchApps(apps, "   "))
    }

    @Test
    fun availableLetters_collectsHeaders() {
        val items = listOf(
            AppListItem.Header('A'),
            AppListItem.App(app("com.a", "A", 'A')),
            AppListItem.Header('Z'),
        )
        assertEquals(listOf('A', 'Z'), repository.availableLetters(items))
    }

    @Test
    fun resolveInstalledPackage_returnsNullForBlankIdentifier() {
        assertNull(repository.resolveInstalledPackage("  "))
    }

    @Test
    fun launchApp_returnsFalseWhenNoLaunchIntent() {
        val info = app("com.nonexistent.fake", "Fake", 'F')
        assertTrue(!repository.launchApp(info, AppSettings(), fullscreen = false))
    }

    private fun app(packageName: String, label: String, letter: Char): AppInfo =
        AppInfo(
            packageName = packageName,
            label = label,
            letter = letter,
            icon = ColorDrawable(0xFF000000.toInt()),
        )
}
