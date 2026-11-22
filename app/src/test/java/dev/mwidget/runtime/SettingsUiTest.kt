package dev.mwidget.runtime

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Spinner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsUiTest {
    private val app: Application get()=RuntimeEnvironment.getApplication()
    private fun all(view: View): List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList())
    private fun eventually(check: ()->Boolean) {
        val end=System.nanoTime()+10_000_000_000L
        while(System.nanoTime()<end) { shadowOf(Looper.getMainLooper()).idle(); if(check()) return; Thread.sleep(20) }
        fail("UI did not reach expected state")
    }
    @Test fun galleryRendersPreviewsAndSurvivesRecreationWithoutChangingPreferences() {
        ThemeStore.ensureDefault(app)
        ThemeStore.importTheme(app,File(System.getProperty("mwidget.samples"),"tape-deck.mwidget").inputStream())
        val prefs=ThemeStore.prefs(app)
        prefs.edit().putString("widget.77.theme","tape-deck").putString("widget.77.player","fixture.absent").commit()
        val before=prefs.all.toMap()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        fun previews()=all(controller.get().window.decorView).filterIsInstance<ThemePreview>()
        eventually { previews().size==2 && previews().all { it.drawable is BitmapDrawable } }
        controller.recreate().visible()
        eventually { previews().size==2 && previews().all { it.drawable is BitmapDrawable } }
        assertEquals(before,prefs.all)
        controller.pause().stop().destroy()
    }
    @Test fun brokenThemeDoesNotHideHealthyThemesAndFailedPreviewExplainsRecovery() {
        ThemeStore.ensureDefault(app)
        ThemeStore.prefs(app).edit().putString("theme.broken","missing-revision").commit()
        assertEquals(listOf("nocturne"),ThemeStore.list(app).map { it.id })
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        eventually { all(controller.get().window.decorView).filterIsInstance<ThemePreview>().any { it.drawable is BitmapDrawable } }
        assertTrue(all(controller.get().window.decorView).filterIsInstance<TextView>().any { it.text.contains("읽을 수 없는 테마") })
        val source=ThemeStore.get(app,"nocturne")
        val invalid=Theme(File(app.cacheDir,"missing-assets"),source.json)
        val image=all(controller.get().window.decorView).filterIsInstance<ThemePreview>().first()
        var message: String?=null
        image.show(invalid) { message=it }
        eventually { message?.contains("다시 시도")==true }
        assertNull(image.drawable)
        image.show(source) { message=it }
        eventually { image.drawable is BitmapDrawable && message==null }
        controller.pause().stop().destroy()
    }

    @Test fun configurationRetainsUnsavedSelectionsAndAbsentPlayerAcrossRecreation() {
        ThemeStore.ensureDefault(app)
        ThemeStore.importTheme(app,File(System.getProperty("mwidget.samples"),"tape-deck.mwidget").inputStream())
        val manager=shadowOf(AppWidgetManager.getInstance(app))
        val id=manager.createWidget(MusicWidget::class.java,R.layout.widget)
        val prefs=ThemeStore.prefs(app)
        prefs.edit().putString("widget.$id.theme","tape-deck").putString("widget.$id.player","fixture.absent").commit()
        val controller=Robolectric.buildActivity(ConfigureActivity::class.java,Intent(app,ConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)).setup().visible()
        fun spinners()=all(controller.get().window.decorView).filterIsInstance<Spinner>()
        eventually { spinners().size==2 && spinners()[0].selectedItem=="Tape Deck" && spinners()[1].selectedItem?.toString()?.contains("fixture.absent")==true }
        spinners()[1].setSelection(0)
        shadowOf(Looper.getMainLooper()).idle()
        controller.recreate().visible()
        eventually { spinners().size==2 && spinners()[0].selectedItem=="Tape Deck" && spinners()[1].selectedItem?.toString()?.startsWith("자동")==true }
        assertEquals("fixture.absent",prefs.getString("widget.$id.player",null))
        assertEquals("tape-deck",prefs.getString("widget.$id.theme",null))
        controller.pause().stop().destroy()
    }
}
