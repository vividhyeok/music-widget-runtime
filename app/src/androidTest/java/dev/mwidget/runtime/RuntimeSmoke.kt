package dev.mwidget.runtime

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** On-device smoke suite. Run on a disposable emulator using tools/verify-emulator.ps1. */
class RuntimeSmoke : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        try {
            runSuite()
            finish(Activity.RESULT_OK,Bundle().apply { putString("stream","\nPASS: imports, bad archives, rendering, widget isolation, session callbacks, 3 transport buttons, switch\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED,Bundle().apply { putString("stream","\nFAIL: ${error.stackTraceToString()}\n") })
        }
    }

    private fun <T> main(block: () -> T): T {
        val task=FutureTask(Callable(block))
        runOnMainSync(task)
        return task.get()
    }

    private fun eventually(message: String, condition: () -> Boolean) {
        val until=SystemClock.elapsedRealtime()+12000
        while (SystemClock.elapsedRealtime()<until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error(message)
    }

    private fun zip(vararg files: Pair<String,ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            files.forEach { (name,data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
        }
    }.toByteArray()

    private fun tapePackage(): ByteArray {
        val assets=context.assets
        val paths=listOf("manifest.json","LICENSE.txt")+assets.list("assets")!!.map { "assets/$it" }
        return zip(*paths.map { it to assets.open(it).use { input -> input.readBytes() } }.toTypedArray())
    }

    private fun runSuite() {
        val app=targetContext
        val manager=AppWidgetManager.getInstance(app)
        val prefs=ThemeStore.prefs(app)
        ThemeStore.ensureDefault(app)
        val tape=tapePackage()
        val second=ThemeStore.importTheme(app,ByteArrayInputStream(tape))
        check(second.id=="tape-deck")
        val before=prefs.getString("theme.tape-deck",null)
        for(bad in listOf(zip("../escape" to byteArrayOf(1)),zip("assets/bomb.png" to ByteArray(2*1024*1024)),zip("manifest.json" to "{\"id\":\"x\",\"id\":\"y\"}".toByteArray()))) {
            check(runCatching { ThemeStore.importTheme(app,ByteArrayInputStream(bad)) }.isFailure)
        }
        check(prefs.getString("theme.tape-deck",null)==before)
        val original=ThemeStore.get(app,"nocturne")
        val rendered=Renderer.render(original,Song(title="테스트곡"),560,280)
        val renderedSecond=Renderer.render(second,Song(title="테스트곡"),560,280)
        check(rendered.getPixel(260,125)!=renderedSecond.getPixel(260,125))
        rendered.recycle(); renderedSecond.recycle()
        check(Sessions.allowed(app)) { "Grant listener access using verify-emulator.ps1" }

        val commands=LinkedBlockingQueue<String>()
        val session=MediaSession(app,"Runtime test player")
        val flags=PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        fun state(playing: Boolean) = session.setPlaybackState(PlaybackState.Builder().setActions(flags)
            .setState(if(playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,0,if(playing) 1f else 0f).build())
        fun track(title: String) = session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST,"Runtime Smoke").putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,
                Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }).build())
        session.setCallback(object: MediaSession.Callback() {
            override fun onPlay() { state(true); commands.add("play") }
            override fun onPause() { state(false); commands.add("pause") }
            override fun onSkipToNext() { track("Fixture next"); commands.add("next") }
            override fun onSkipToPrevious() { track("Fixture previous"); commands.add("previous") }
        },Handler(Looper.getMainLooper()))
        track("Fixture initial"); state(false); session.isActive=true
        val host=main { AppWidgetHost(app,7518).apply { startListening() } }
        val ids=IntArray(2) { host.allocateAppWidgetId() }
        try {
            ids.forEach { id ->
                val options=Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,360)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,180)
                }
                check(manager.bindAppWidgetIdIfAllowed(id,MusicWidget.component(app),options)) { "Grant bind permission using verify-emulator.ps1" }
            }
            prefs.edit().putString("widget.${ids[0]}.theme","nocturne").putString("widget.${ids[1]}.theme","tape-deck")
                .putString("widget.${ids[1]}.player",app.packageName).commit()
            val views=ids.map { id -> main { host.createView(app,id,manager.getAppWidgetInfo(id)) } }
            eventually("Fixture session not visible") { Sessions.list(app).any { it.packageName==app.packageName } }
            MusicWidget.refresh(app)
            fun description(v: AppWidgetHostView) = main { v.findViewById<android.view.View>(R.id.canvas)?.contentDescription?.toString().orEmpty() }
            eventually("Widget initial metadata not rendered") { views.all { description(it).contains("Fixture initial") } }
            fun click(v: AppWidgetHostView, index: Int) = main {
                val hits=v.findViewById<ViewGroup>(R.id.hits)
                val button=hits.getChildAt(index)
                check(button.layoutParams.width>0 && button.layoutParams.height>0)
                check(button.performClick())
            }
            click(views[0],1); check(commands.poll(8,TimeUnit.SECONDS)=="play")
            eventually("Playback callback did not redraw both widgets") { views.all { description(it).contains("재생 중") } }
            click(views[1],1); check(commands.poll(8,TimeUnit.SECONDS)=="pause")
            click(views[1],2); check(commands.poll(8,TimeUnit.SECONDS)=="next")
            eventually("Next metadata not rendered") { views.all { description(it).contains("Fixture next") } }
            click(views[0],0); check(commands.poll(8,TimeUnit.SECONDS)=="previous")
            eventually("Previous metadata not rendered") { views.all { description(it).contains("Fixture previous") } }
            click(views[0],3)
            eventually("Player switch not saved") { prefs.getString("widget.${ids[0]}.player",null)==app.packageName }
            click(views[1],3)
            eventually("Second widget should return to automatic") { !prefs.contains("widget.${ids[1]}.player") }
            check(prefs.getString("widget.${ids[0]}.theme",null)=="nocturne" && prefs.getString("widget.${ids[1]}.theme",null)=="tape-deck")
        } finally {
            main { host.stopListening(); host.deleteHost() }
            session.release()
        }
    }
}
