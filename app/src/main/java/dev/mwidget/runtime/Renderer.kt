package dev.mwidget.runtime

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.*

data class Song(val title: String="음악을 재생하세요", val artist: String="플레이어의 활성 세션을 기다리는 중", val album: String="", val player: String="", val status: String="대기 중", val playing: Boolean=false, val art: Bitmap?=null, val position: Long=0, val duration: Long=0)
data class Geometry(val scale: Float,val left: Float,val top: Float) {
    companion object {
        fun fit(width: Float,height: Float,canvasW: Int,canvasH: Int): Geometry {
            val s=min(width/canvasW,height/canvasH)
            return Geometry(s,(width-canvasW*s)/2,(height-canvasH*s)/2)
        }
    }
}

/** Shared renderer for widget output, host frames and Android evidence. */
object Renderer {
    private fun time(value: Long)="%d:%02d".format(java.util.Locale.ROOT,value/60000,value/1000%60)
    fun render(theme: Theme,song: Song,width: Int,height: Int,timeMs: Long=0,animate: Boolean=false): Bitmap {
        require(width in 1..1440 && height in 1..1440 && width.toLong()*height<=1048576)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            val c=Canvas(bitmap); val g=Geometry.fit(width.toFloat(),height.toFloat(),theme.width,theme.height)
            c.translate(g.left,g.top); c.scale(g.scale,g.scale)
            val painter=Painter(theme,song,timeMs,animate)
            try { painter.draw(c,theme.json.getJSONArray("layers")) } finally { painter.close() }
            return bitmap
        } catch(e: Throwable) { bitmap.recycle(); throw e }
    }
    private class Painter(val theme: Theme,val song: Song,val timeMs: Long,val animate: Boolean) {
        private val fonts=mutableMapOf<String,Typeface>()
        private val images=mutableMapOf<String,Bitmap>()
        private fun image(src: String)=images.getOrPut(src) { requireNotNull(BitmapFactory.decodeFile(File(theme.dir,src).path)) { "이미지를 읽을 수 없음: $src" } }
        fun close() { images.values.forEach { it.recycle() } }
        private fun paint()=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private fun phase(a: JSONObject)=((timeMs%a.getLong("durationMs")).toDouble()/a.getLong("durationMs")).toFloat()
        fun draw(c: Canvas,array: JSONArray) { for(i in 0 until array.length()) drawNode(c,array.getJSONObject(i)) }
        private fun drawNode(c: Canvas,l: JSONObject) {
            if(!Scene.visible(l,song)) return
            val w=l.getDouble("w").toFloat(); val h=l.getDouble("h").toFloat(); val rect=RectF(0f,0f,w,h)
            val checkpoint=c.save()
            try {
                c.concat(Scene.local(l))
                val a=l.optJSONObject("animation")?.takeIf { animate && (it.getString("when")=="always" || song.playing) }
                var alpha=l.optDouble("opacity",1.0).toFloat()
                if(a!=null) {
                    val f=phase(a)
                    when(a.getString("kind")) {
                        "rotate" -> c.rotate(a.optDouble("from",0.0).toFloat()+(a.optDouble("to",360.0)-a.optDouble("from",0.0)).toFloat()*f,w/2,h/2)
                        "translate" -> { val ping=1-abs(2*f-1); c.translate(a.optDouble("dx",0.0).toFloat()*ping,a.optDouble("dy",0.0).toFloat()*ping) }
                        "pulse" -> { val ping=1-abs(2*f-1); alpha*=a.optDouble("from",0.35).toFloat()+(a.optDouble("to",1.0)-a.optDouble("from",0.35)).toFloat()*ping }
                    }
                }
                if(alpha<1f || l.has("maskSrc")) c.saveLayerAlpha(null,(alpha*255).roundToInt().coerceIn(0,255))
                if(l.has("maskSrc")) c.clipRect(rect)
                val path=Path()
                when(l.optString("mask","none")) {
                    "circle" -> { path.addCircle(w/2,h/2,min(w,h)/2,Path.Direction.CW); c.clipPath(path) }
                    "rounded" -> { val r=l.optDouble("radius",min(w,h)/8.0).toFloat(); path.addRoundRect(rect,r,r,Path.Direction.CW); c.clipPath(path) }
                }
                when(l.getString("type")) {
                    "group" -> draw(c,l.getJSONArray("children"))
                    "text" -> text(c,l,w,h)
                    "shape" -> shape(c,l,rect)
                    "equalizer" -> equalizer(c,l,w,h)
                    "progress" -> progress(c,l,rect)
                    else -> {
                        val art=l.getString("type")=="artwork"
                        var src=l.optString("src",l.optString("fallback"))
                        if(l.getString("type")=="icon" && l.getString("action")=="toggle" && song.playing) src=l.optString("playingSrc",src)
                        if(a?.getString("kind")=="frames") { val sources=a.getJSONArray("sources"); src=sources.getString((phase(a)*sources.length()).toInt().coerceAtMost(sources.length()-1)) }
                        val bitmap=if(art && song.art!=null && a?.getString("kind")!="frames") song.art else image(src)
                        val fit=l.optString("fit",if(art) "crop" else "stretch")
                        c.save(); c.clipRect(rect)
                        if(fit=="stretch") c.drawBitmap(bitmap,null,rect,paint()) else {
                            val s=if(fit=="crop") max(w/bitmap.width,h/bitmap.height) else min(w/bitmap.width,h/bitmap.height)
                            val iw=bitmap.width*s; val ih=bitmap.height*s
                            c.drawBitmap(bitmap,null,RectF((w-iw)/2,(h-ih)/2,(w+iw)/2,(h+ih)/2),paint())
                        }
                        c.restore()
                    }
                }
                if(l.has("maskSrc")) c.drawBitmap(image(l.getString("maskSrc")),null,rect,paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            } finally { c.restoreToCount(checkpoint) }
        }
        private fun text(c: Canvas,l: JSONObject,w: Float,h: Float) {
            val value=when(l.getString("field")) { "literal"->l.getString("text"); "elapsed"->if(song.duration>0) time(song.position) else "—"; "duration"->if(song.duration>0) time(song.duration) else "—"; "title"->song.title; "artist"->song.artist; "album"->song.album; "player"->song.player; else->song.status }.take(2048)
            val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color=Color.parseColor(l.getString("color")); textSize=l.getDouble("size").toFloat()
                typeface=fonts.getOrPut(l.getString("font")) { Typeface.createFromFile(File(theme.dir,l.getString("font"))) }
                letterSpacing=l.optDouble("letterSpacing",0.0).toFloat()
            }
            val overflow=l.optString("overflow","ellipsis")
            val align=when(l.optString("align","left")) { "center" -> Layout.Alignment.ALIGN_CENTER; "right" -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
            c.save(); c.clipRect(0f,0f,w,h)
            if(overflow=="wrap") {
                val layout=StaticLayout.Builder.obtain(value,0,value.length,p,max(1,w.toInt())).setAlignment(align).setIncludePad(false)
                    .setLineSpacing(0f,l.optDouble("lineSpacing",1.0).toFloat()).setMaxLines(l.optInt("maxLines",3)).setEllipsize(TextUtils.TruncateAt.END).build()
                val y=when(l.optString("valign","top")) { "center" -> (h-layout.height)/2; "bottom" -> h-layout.height; else -> 0f }
                c.translate(0f,y); layout.draw(c)
            } else {
                val canScroll=overflow=="marquee" && animate && song.playing && p.measureText(value)>w
                val line=if(overflow=="clip" || canScroll) value else TextUtils.ellipsize(value,p,w,TextUtils.TruncateAt.END).toString()
                val tw=p.measureText(line); val fm=p.fontMetrics
                val baseline=when(l.optString("valign","top")) { "center" -> (h-fm.descent-fm.ascent)/2; "bottom" -> h-fm.descent; else -> -fm.ascent }
                var x=when(l.optString("align","left")) { "center" -> (w-tw)/2; "right" -> w-tw; else -> 0f }
                if(canScroll) { val distance=tw+w/3; x=-(timeMs%12000)/12000f*distance; c.drawText(line,x+distance,baseline,p) }
                c.drawText(line,x,baseline,p)
            }
            c.restore()
        }
        private fun shape(c: Canvas,l: JSONObject,r: RectF) {
            val p=paint().apply { color=Color.parseColor(l.getString("color")) }; val path=Path()
            when(l.getString("shape")) {
                "ellipse" -> path.addOval(r,Path.Direction.CW)
                "polygon" -> { val points=l.getJSONArray("points"); path.moveTo(points.getDouble(0).toFloat()*r.width(),points.getDouble(1).toFloat()*r.height()); for(n in 2 until points.length() step 2) path.lineTo(points.getDouble(n).toFloat()*r.width(),points.getDouble(n+1).toFloat()*r.height()); path.close() }
                else -> { val radius=l.optDouble("radius",0.0).toFloat(); path.addRoundRect(r,radius,radius,Path.Direction.CW) }
            }
            l.optJSONObject("gradient")?.let { g ->
                val angle=Math.toRadians(g.getDouble("angle")); val dx=cos(angle).toFloat()*r.width()/2; val dy=sin(angle).toFloat()*r.height()/2
                p.shader=LinearGradient(r.centerX()-dx,r.centerY()-dy,r.centerX()+dx,r.centerY()+dy,p.color,Color.parseColor(g.getString("endColor")),Shader.TileMode.CLAMP)
            }
            c.drawPath(path,p)
            if(l.has("strokeColor") && l.optDouble("strokeWidth",0.0)>0) { p.shader=null; p.color=Color.parseColor(l.getString("strokeColor")); p.style=Paint.Style.STROKE; p.strokeWidth=l.getDouble("strokeWidth").toFloat(); c.drawPath(path,p) }
        }
        private fun equalizer(c: Canvas,l: JSONObject,w: Float,h: Float) {
            val p=paint().apply { color=Color.parseColor(l.getString("color")) }
            val bars=l.getInt("bars"); val gap=l.optDouble("gap",0.25).toFloat(); val seed=l.optInt("seed",7)
            val t=if(animate && song.playing && l.getString("mode")=="simulated") timeMs/1000.0 else 0.0
            for(i in 0 until bars) { val value=0.15+0.85*abs(sin(i*1.73+seed+t*(1+i%3))); c.drawRect(i*w/bars,h*(1-value).toFloat(),(i+1-gap)*w/bars,h,p) }
        }
        private fun progress(c: Canvas,l: JSONObject,r: RectF) {
            val w=r.width(); val h=r.height(); val f=if(song.duration>0) (song.position.toDouble()/song.duration).coerceIn(0.0,1.0).toFloat() else 0f
            val p=paint(); val radius=l.optDouble("radius",h/2.0).toFloat(); val direction=l.optString("direction","horizontal")
            if(direction=="circular") {
                p.style=Paint.Style.STROKE; p.strokeWidth=l.optDouble("strokeWidth",6.0).toFloat().coerceAtMost(min(w,h)); p.strokeCap=Paint.Cap.ROUND
                val arc=RectF(r).apply { inset(p.strokeWidth/2,p.strokeWidth/2) }
                val start=l.optDouble("startAngle",-90.0).toFloat(); val sweep=l.optDouble("sweepAngle",360.0).toFloat()
                p.color=Color.parseColor(l.getString("trackColor")); c.drawArc(arc,start,sweep,false,p)
                if(f>0 && l.optBoolean("showFill",true)) { p.color=Color.parseColor(l.getString("color")); c.drawArc(arc,start,sweep*f,false,p) }
                return
            }
            fun asset(key: String,rect: RectF) { c.drawBitmap(image(l.getString(key)),null,rect,p) }
            if(l.has("trackSrc")) asset("trackSrc",r) else { p.color=Color.parseColor(l.getString("trackColor")); c.drawRoundRect(r,radius,radius,p) }
            val fill=if(direction=="vertical") RectF(0f,h*(1-f),w,h) else RectF(0f,0f,w*f,h)
            if(song.duration>0 && l.optBoolean("showFill",true) && f>0) {
                p.color=Color.parseColor(l.getString("color"))
                if(l.has("fillSrc")) { c.save(); c.clipRect(fill); asset("fillSrc",r); c.restore() } else c.drawRoundRect(fill,radius,radius,p)
            }
            if(l.has("thumbSrc") && song.duration>0) {
                val tw=l.getDouble("thumbWidth").toFloat()
                if(direction=="vertical") { val th=min(tw,h); val top=(h-th)*(1-f); asset("thumbSrc",RectF(0f,top,w,top+th)) }
                else { val left=(w-tw)*f; asset("thumbSrc",RectF(left,0f,left+tw,h)) }
            }
        }
    }
}
