package dev.mwidget.runtime

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeDeletionTest {
    private val app: Application get()=RuntimeEnvironment.getApplication()
    private fun tape()=ThemeStore.importTheme(app,File(System.getProperty("mwidget.samples"),"tape-deck.mwidget").inputStream())
    @Test fun deletingRemovesEveryRevisionButKeepsOtherThemesAndPlayers() {
        ThemeStore.ensureDefault(app)
        val original=ThemeStore.get(app,"nocturne")
        val first=tape(); val second=tape()
        assertNotEquals(first.dir,second.dir)
        val prefs=ThemeStore.prefs(app)
        prefs.edit().putString("widget.44.theme","tape-deck").putString("widget.44.player","fixture.player")
            .putString("suggestedTheme","tape-deck").commit()
        assertTrue(ThemeStore.delete(app,"tape-deck","nocturne"))
        assertFalse(first.dir.exists()); assertFalse(second.dir.exists())
        assertEquals(original.dir,ThemeStore.get(app,"nocturne").dir)
        assertEquals("nocturne",prefs.getString("widget.44.theme",null))
        assertEquals("fixture.player",prefs.getString("widget.44.player",null))
        assertFalse(prefs.contains("suggestedTheme"))
        assertFalse(ThemeStore.list(app).any { it.id=="tape-deck" })
    }
    @Test fun usedThemeRequiresAnExistingDifferentReplacementBeforeAnyMutation() {
        ThemeStore.ensureDefault(app); val theme=tape()
        val prefs=ThemeStore.prefs(app); prefs.edit().putString("widget.44.theme","tape-deck").commit()
        val before=prefs.all.toMap()
        for(replacement in listOf(null,"tape-deck","absent")) {
            assertThrows(Exception::class.java) { ThemeStore.delete(app,"tape-deck",replacement) }
            assertEquals(before,prefs.all); assertTrue(theme.dir.exists())
        }
    }
    @Test fun deletedDefaultStaysDeletedUntilExplicitRestore() {
        ThemeStore.ensureDefault(app); val theme=ThemeStore.get(app,"nocturne")
        assertTrue(ThemeStore.delete(app,"nocturne"))
        ThemeStore.ensureDefault(app)
        assertTrue(ThemeStore.list(app).isEmpty()); assertFalse(theme.dir.exists())
        ThemeStore.restoreDefault(app)
        assertEquals(listOf("nocturne"),ThemeStore.list(app).map { it.id })
        assertFalse(ThemeStore.prefs(app).getBoolean("default.dismissed",false))
    }
}
