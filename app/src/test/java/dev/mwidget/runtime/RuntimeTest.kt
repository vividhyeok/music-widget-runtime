package dev.mwidget.runtime

import android.app.Application
import android.app.NotificationManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuntimeTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val tape get() = File(System.getProperty("mwidget.samples"),"tape-deck.mwidget")

    @Test fun v2OrbitImportsRendersAndHonorsMotionBudget() {
        val theme=ThemeStore.importTheme(context,File(System.getProperty("mwidget.samples"),"orbit.mwidget").inputStream())
        assertEquals(2,theme.json.getInt("version"))
        val song=Song(title="달빛 아래 긴 한국어 제목과 English Title",playing=true,position=50,duration=100)
        val first=Renderer.render(theme,song,720,360,0,true)
        val later=Renderer.render(theme,song,720,360,3000,true)
        assertFalse(first.sameAs(later))
        val paused=song.copy(playing=false)
        assertTrue(Renderer.render(theme,paused,720,360,0,true).sameAs(Renderer.render(theme,paused,720,360,3000,true)))
        assertEquals(1,Dynamics.plan(theme,song,false).frames)
        assertEquals(1,Dynamics.plan(theme,paused).frames)
        val plan=Dynamics.plan(theme,song)
        assertEquals(12,plan.frames)
        val tiles=HybridHost.tiles(theme,song,360,180,plan,900000)
        assertNotNull(tiles)
        assertTrue(tiles!!.any { it.frames.size>1 })
        assertNull(HybridHost.tiles(theme,song,360,180,plan,10))
        val root=MusicWidget.render(context,987,theme,song,null,SizeF(180f,90f),null).apply(context,FrameLayout(context))
        assertEquals(1,root.findViewById<ViewGroup>(R.id.hits).childCount)
        assertTrue(root.findViewById<ViewGroup>(R.id.motion).childCount>0)
        fun noError(view: View) { assertFalse(view is android.widget.TextView && view.text.toString().contains("Problem loading")); if(view is ViewGroup) for(n in 0 until view.childCount) noError(view.getChildAt(n)) }
        noError(root)
        val reportDir=File(System.getProperty("mwidget.samples"),"../app/build/reports/expression").apply { mkdirs() }
        File(reportDir,"orbit-playing-android-native.png").outputStream().use { first.compress(Bitmap.CompressFormat.PNG,100,it) }
        File(reportDir,"orbit-frame-android-native.png").outputStream().use { later.compress(Bitmap.CompressFormat.PNG,100,it) }
        first.recycle(); later.recycle(); tiles.forEach { it.frames.forEach { tile -> tile.bitmap.recycle() } }
    }

    @Test fun v2TransformsMaskOpacityAndTouchUseSameCoordinates() {
        val dir=File(context.cacheDir,"scene-test").apply { mkdirs() }
        val json=org.json.JSONObject("""{"version":2,"id":"geometry","name":"Geometry","width":400,"height":240,
            "layers":[{"type":"group","x":100,"y":50,"w":100,"h":50,"transform":{"rotation":90},"children":[
                {"id":"button","type":"shape","shape":"rect","x":0,"y":0,"w":100,"h":50,"color":"#ff0000","opacity":0.5,"mask":"circle"}]}],
            "touch":[{"target":"button","action":"toggle","label":"Play"}]}""")
        File(dir,"manifest.json").writeText(json.toString())
        val theme=ThemeStore.validate(dir)
        val hit=Scene.hit(theme,json.getJSONArray("touch").getJSONObject(0))
        assertEquals(125f,hit.left,0.01f); assertEquals(25f,hit.top,0.01f)
        assertEquals(50f,hit.width(),0.01f); assertEquals(100f,hit.height(),0.01f)
        val image=Renderer.render(theme,Song(),400,240)
        assertTrue(kotlin.math.abs(128-Color.alpha(image.getPixel(150,75)))<=1)
        assertEquals(Color.TRANSPARENT,image.getPixel(126,26))
        val node=json.getJSONArray("layers").getJSONObject(0)
        node.put("animation",org.json.JSONObject("""{"kind":"rotate","durationMs":6000,"steps":6,"when":"playing","fallback":"static"}"""))
        File(dir,"manifest.json").writeText(json.toString())
        assertThrows(Exception::class.java) { ThemeStore.validate(dir) }
        image.recycle()
    }

    @Test fun v2RejectsUnsafeOrUnrunnableDeclarations() {
        val dir=File(context.cacheDir,"invalid-v2").apply { mkdirs() }
        val base="""{"version":2,"id":"invalid","name":"Invalid","width":400,"height":240,"layers":[NODE],"touch":[]}"""
        val node="""{"type":"shape","shape":"rect","x":0,"y":0,"w":100,"h":100,"color":"#ffffff"EXTRA}"""
        for(extra in listOf(
            ",\"transform\":{\"quad\":[0,0,1,1,1,0,0,1]}",
            ",\"transform\":{\"skewX\":1,\"skewY\":1}",
            ",\"transform\":false",
            ",\"animation\":{\"kind\":\"pulse\",\"durationMs\":6000,\"steps\":6,\"when\":\"playing\",\"fallback\":\"static\",\"from\":2}",
            ",\"animation\":{\"kind\":\"javascript\",\"durationMs\":6000,\"steps\":6,\"when\":\"playing\",\"fallback\":\"static\"}"
        )) {
            File(dir,"manifest.json").writeText(base.replace("NODE",node.replace("EXTRA",extra)))
            assertThrows(Exception::class.java) { ThemeStore.validate(dir) }
        }
    }

    @Test fun customProgressThumbMovesAndStaysInsideTrack() {
        ThemeStore.ensureDefault(context)
        val source=ThemeStore.get(context,"nocturne")
        val json=org.json.JSONObject(source.json.toString())
        json.put("layers",org.json.JSONArray().put(org.json.JSONObject("""
            {"type":"progress","x":40,"y":40,"w":300,"h":40,
             "color":"#ffffff","trackColor":"#000000","showFill":false,
             "thumbSrc":"assets/play.png","thumbWidth":40,"radius":0}
        """)))
        val theme=Theme(source.dir,json)
        val start=Renderer.render(theme,Song(position=0,duration=100),720,360)
        val end=Renderer.render(theme,Song(position=100,duration=100),720,360)
        assertFalse(start.sameAs(end))
        assertEquals(Color.BLACK,end.getPixel(60,60))
        assertEquals(Color.BLACK,start.getPixel(320,60))
        assertEquals(Color.TRANSPARENT,end.getPixel(341,60))
        assertTrue(end.sameAs(Renderer.render(theme,Song(position=200,duration=100),720,360)))
    }

    @Test fun packagedThemesRenderWithIndependentButtons() {
        ThemeStore.ensureDefault(context)
        val first=ThemeStore.get(context,"nocturne")
        val second=ThemeStore.importTheme(context,tape.inputStream())
        val artwork=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val song=Song(title="테스트곡",artist="Test artist",art=artwork)
        val firstBitmap=Renderer.render(first,song,720,360)
        val secondBitmap=Renderer.render(second,song,720,360)
        assertEquals(Color.MAGENTA,firstBitmap.getPixel(125,185))
        assertEquals(Color.MAGENTA,secondBitmap.getPixel(580,150))
        assertNotEquals(firstBitmap.getPixel(252,112),secondBitmap.getPixel(252,112))
        val playing=Renderer.render(first,song.copy(playing=true),720,360)
        assertFalse(firstBitmap.sameAs(playing))
        for(theme in listOf(first,second)) {
            val view=MusicWidget.render(context,123,theme,song,"test.player",SizeF(360f,180f),null)
                .apply(context,FrameLayout(context))
            val hits=view.findViewById<ViewGroup>(R.id.hits)
            assertEquals(5,hits.childCount)
            assertEquals("재생",hits.getChildAt(1).contentDescription)
            val firstHit=hits.getChildAt(0).layoutParams as ViewGroup.MarginLayoutParams
            val toggleHit=hits.getChildAt(1).layoutParams as ViewGroup.MarginLayoutParams
            assertTrue(firstHit.leftMargin+firstHit.width<=toggleHit.leftMargin)
            assertTrue(hits.getChildAt(1).hasOnClickListeners())
            assertTrue(view.findViewById<View>(R.id.canvas).contentDescription.contains("테스트곡"))
        }
    }

    @Test fun rejectsInvalidArchivesAndKeepsInstalledTheme() {
        ThemeStore.importTheme(context,tape.inputStream())
        val installed=ThemeStore.get(context,"tape-deck").dir
        val entries=ZipFile(tape).use { zip ->
            zip.entries().asSequence().associate { it.name to zip.getInputStream(it).use { stream -> stream.readBytes() } }
        }
        fun archive(data: Map<String,ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip -> data.forEach { (name,bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            } }
        }.toByteArray()
        val large=ByteArrayOutputStream().also { output ->
            val image=Bitmap.createBitmap(2049,3,Bitmap.Config.ARGB_8888)
            image.compress(Bitmap.CompressFormat.PNG,100,output); image.recycle()
        }.toByteArray()
        for (bad in listOf(
            entries+mapOf("../escape" to byteArrayOf(1)),
            entries+mapOf("MANIFEST.JSON" to byteArrayOf(1)),
            entries+mapOf("assets/background.png" to ByteArray(2*1024*1024)),
            entries+mapOf("assets/background.png" to large)
        )) {
            assertThrows(Exception::class.java) { ThemeStore.importTheme(context,ByteArrayInputStream(archive(bad))) }
            assertEquals(installed,ThemeStore.get(context,"tape-deck").dir)
        }
        val updatedManifest=entries.getValue("manifest.json").decodeToString().replace("Tape Deck","Tape Deck Revised")
        ThemeStore.importTheme(context,ByteArrayInputStream(archive(entries+mapOf("manifest.json" to updatedManifest.encodeToByteArray()))))
        assertEquals("Tape Deck Revised",ThemeStore.get(context,"tape-deck").json.getString("name"))
    }

    @Test fun fixedPlayerDoesNotFallThroughToAnotherSession() {
        val permissions=shadowOf(context.getSystemService(NotificationManager::class.java))
        val manager=shadowOf(context.getSystemService(MediaSessionManager::class.java))
        fun controller(pkg: String, playing: Boolean): MediaController = Shadow.newInstanceOf(MediaController::class.java).also {
            shadowOf(it).setPackageName(pkg)
            shadowOf(it).setPlaybackState(PlaybackState.Builder().setState(
                if(playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,0L,if(playing) 1f else 0f
            ).build())
            manager.addController(it)
        }
        val paused=controller("fixture.paused",false)
        val playing=controller("fixture.playing",true)
        assertTrue(Sessions.list(context).isEmpty())
        permissions.setNotificationListenerAccessGranted(Sessions.component(context),true)
        val sessions=Sessions.list(context)
        assertSame(playing,Sessions.pick(sessions,null))
        assertSame(paused,Sessions.pick(sessions,"fixture.paused"))
        assertNull(Sessions.pick(sessions,"fixture.missing"))
    }
}
