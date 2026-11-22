package dev.mwidget.runtime

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.TextPaint
import android.util.SizeF
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Explicit instrumentation input, private output only; no exported runtime endpoint. */
object ThemeCapture {
    fun run(app: Context,main: (() -> Unit)->Unit) {
        val input=File(app.filesDir,"qa-input.mwidget")
        val theme=ThemeStore.importTheme(app,input.inputStream())
        val output=File(app.filesDir,"qa").apply { mkdirs() }
        val variants=listOf(
            "playing" to Song(title="Midnight in Seoul",artist="Luna Park",playing=true,position=84000,duration=222000),
            "paused" to Song(title="일시 정지한 음악",artist="아티스트",playing=false,position=84000,duration=222000),
            "long-ko" to Song(title="아주 긴 한국어 노래 제목과 English Mixed Title 0123456789 — 잘림과 정렬 검사",artist="이름이 아주 긴 아티스트와 함께하는 음악",playing=true,position=84000,duration=222000),
            "missing" to Song(title="",artist="",duration=0),
            "artwork" to Song(title="앨범아트 검사",artist="Artist",playing=true,art=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) })
        )
        fun save(name: String,image: Bitmap) { File(output,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle() }
        val plan=Dynamics.plan(theme,variants.first().second)
        val hostPlan=Dynamics.plan(theme,variants.first().second,Dynamics.enabled(app))
        val report=JSONObject().put("renderer","Android Canvas").put("host","AppWidgetHostView inflation; launcher placement is separate")
            .put("theme",theme.id).put("motionPlan",hostPlan.reason).put("width",theme.width).put("height",theme.height)
        val text=JSONArray()
        for(node in Scene.nodes(theme.json.getJSONArray("layers"))) if(node.getString("type")=="text") {
            val p=TextPaint().apply { textSize=node.getDouble("size").toFloat(); typeface=Typeface.createFromFile(File(theme.dir,node.getString("font"))) }
            text.put(JSONObject().put("field",node.getString("field")).put("fontHeight",p.fontMetrics.descent-p.fontMetrics.ascent).put("boxHeight",node.getDouble("h")))
        }
        report.put("text",text)
        for((name,song) in variants) {
            save("renderer-$name",Renderer.render(theme,song,theme.width,theme.height))
            main {
                val provider=AppWidgetManager.getInstance(app).installedProviders.first { it.provider==MusicWidget.component(app) }
                val host=AppWidgetHostView(app).apply { setAppWidget(0,provider); setPadding(0,0,0,0) }
                val density=app.resources.displayMetrics.density
                host.updateAppWidget(MusicWidget.render(app,0,theme,song,null,SizeF(theme.width/density,theme.height/density),null))
                host.measure(View.MeasureSpec.makeMeasureSpec(theme.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(theme.height,View.MeasureSpec.EXACTLY))
                host.layout(0,0,theme.width,theme.height)
                val bitmap=Bitmap.createBitmap(theme.width,theme.height,Bitmap.Config.ARGB_8888)
                host.draw(Canvas(bitmap)); save("host-$name",bitmap)
            }
            song.art?.recycle()
        }
        for(n in 0 until plan.frames) save("frame-$n",Renderer.render(theme,variants.first().second,theme.width,theme.height,n.toLong()*plan.periodMs/plan.frames,true))
        report.put("frames",plan.frames)
        File(output,"android-report.json").writeText(report.toString(2))
    }
}
