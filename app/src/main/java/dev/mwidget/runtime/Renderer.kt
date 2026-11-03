package dev.mwidget.runtime

import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import kotlin.math.min

data class Song(val title: String="음악을 재생하세요", val artist: String="플레이어의 활성 세션을 기다리는 중", val album: String="", val player: String="", val status: String="대기 중", val playing: Boolean=false, val art: Bitmap?=null)

data class Geometry(val scale: Float, val left: Float, val top: Float) {
    companion object {
        fun fit(width: Float,height: Float,canvasW: Int,canvasH: Int): Geometry {
            val s=min(width/canvasW,height/canvasH)
            return Geometry(s,(width-canvasW*s)/2,(height-canvasH*s)/2)
        }
    }
}

object Renderer {
    fun render(theme: Theme,song: Song,width: Int,height: Int): Bitmap {
        require(width in 1..1440 && height in 1..1440 && width.toLong()*height<=1048576)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); val g=Geometry.fit(width.toFloat(),height.toFloat(),theme.width,theme.height)
        canvas.translate(g.left,g.top); canvas.scale(g.scale,g.scale)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val fonts=mutableMapOf<String,Typeface>()
        val layers=theme.json.getJSONArray("layers")
        for(i in 0 until layers.length()) {
            val l=layers.getJSONObject(i)
            val x=l.getDouble("x").toFloat(); val y=l.getDouble("y").toFloat()
            val w=l.getDouble("w").toFloat(); val h=l.getDouble("h").toFloat()
            val target=RectF(x,y,x+w,y+h)
            if(l.getString("type")=="text") {
                val text=when(l.getString("field")) { "title"->song.title; "artist"->song.artist; "album"->song.album; "player"->song.player; else->song.status }.take(2048)
                val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=Color.parseColor(l.getString("color")); textSize=l.getDouble("size").toFloat()
                    typeface=fonts.getOrPut(l.getString("font")) { Typeface.createFromFile(File(theme.dir,l.getString("font"))) }
                }
                val line=TextUtils.ellipsize(text,p,w,TextUtils.TruncateAt.END).toString()
                canvas.save(); canvas.clipRect(target); canvas.drawText(line,x,y-p.fontMetrics.ascent,p); canvas.restore()
            } else {
                val artwork=l.getString("type")=="artwork"
                var src=l.optString("src",l.optString("fallback"))
                if(l.getString("type")=="icon" && l.getString("action")=="toggle" && song.playing) src=l.optString("playingSrc",src)
                val own=!(artwork && song.art!=null)
                val image=if(own) BitmapFactory.decodeFile(File(theme.dir,src).path) else song.art
                requireNotNull(image) { "이미지를 읽을 수 없음: $src" }
                if(artwork) {
                    val s=maxOf(w/image.width,h/image.height)
                    val sw=w/s; val sh=h/s
                    val crop=Rect(((image.width-sw)/2).toInt(),((image.height-sh)/2).toInt(),((image.width+sw)/2).toInt(),((image.height+sh)/2).toInt())
                    canvas.drawBitmap(image,crop,target,paint)
                } else canvas.drawBitmap(image,null,target,paint)
                if(own) image.recycle()
            }
        }
        return bitmap
    }
}
