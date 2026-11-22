package dev.mwidget.runtime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.SizeF
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject

/** Static runs and animated roots retain scene order; cropped tiles bound Binder memory. */
object HybridHost {
    data class Tile(val bitmap: Bitmap,val x: Int,val y: Int)
    data class Segment(val frames: List<Tile>)
    private fun moving(node: JSONObject,song: Song): Boolean {
        if(!Scene.visible(node,song)) return false
        val a=node.optJSONObject("animation")
        return a!=null && (a.getString("when")=="always" || song.playing) ||
            song.playing && (node.optString("overflow")=="marquee" || node.optString("mode")=="simulated") ||
            node.optJSONArray("children")?.let { array -> (0 until array.length()).any { moving(array.getJSONObject(it),song) } }==true
    }
    private fun crop(bitmap: Bitmap): Tile {
        val pixels=IntArray(bitmap.width*bitmap.height); bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        var left=bitmap.width; var right=0; var top=bitmap.height; var bottom=0
        for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) if(Color.alpha(pixels[y*bitmap.width+x])>0) {
            left=minOf(left,x); right=maxOf(right,x+1); top=minOf(top,y); bottom=maxOf(bottom,y+1)
        }
        if(right==0) { bitmap.recycle(); return Tile(Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888),0,0) }
        val bounds=Rect(left,top,right,bottom)
        val tile=Bitmap.createBitmap(bitmap,left,top,bounds.width(),bounds.height())
        if(tile!==bitmap) bitmap.recycle()
        return Tile(tile,left,top)
    }
    fun tiles(theme: Theme,song: Song,w: Int,h: Int,plan: Dynamics.Plan,budget: Int,baseTimeMs: Long=0): List<Segment>? {
        val nodes=theme.json.getJSONArray("layers")
        val runs=mutableListOf<Pair<JSONArray,Boolean>>(); var static=JSONArray()
        for(i in 0 until nodes.length()) {
            val node=nodes.getJSONObject(i)
            if(moving(node,song)) { if(static.length()>0) runs.add(static to false); static=JSONArray(); runs.add(JSONArray().put(node) to true) }
            else static.put(node)
        }
        if(static.length()>0) runs.add(static to false)
        if(runs.count { it.second }>3) return null
        val result=mutableListOf<Segment>(); val owned=mutableListOf<Bitmap>(); var total=0L
        try {
            for((array,moving) in runs) {
                val part=Theme(theme.dir,JSONObject(theme.json.toString()).put("layers",array))
                val frames=(0 until if(moving) plan.frames else 1).map { n ->
                    val interval=maxOf(500,plan.periodMs/plan.frames)
                    val start=(baseTimeMs/interval%plan.frames)*interval
                    val tile=crop(Renderer.render(part,song,w,h,start+n.toLong()*plan.periodMs/plan.frames,moving))
                    owned.add(tile.bitmap); total+=tile.bitmap.width.toLong()*tile.bitmap.height
                    if(total>budget) { owned.forEach { it.recycle() }; return null }
                    tile
                }
                result.add(Segment(frames))
            }
            return result
        } catch(e: Throwable) { owned.forEach { if(!it.isRecycled) it.recycle() }; throw e }
    }
    fun views(c: Context,segments: List<Segment>,size: SizeF,w: Int,h: Int,plan: Dynamics.Plan): List<RemoteViews> {
        fun frame(tile: Tile)=RemoteViews(c.packageName,R.layout.frame).apply {
            setImageViewBitmap(R.id.frame,tile.bitmap)
            setViewLayoutWidth(R.id.frame,tile.bitmap.width*size.width/w,COMPLEX_UNIT_DIP)
            setViewLayoutHeight(R.id.frame,tile.bitmap.height*size.height/h,COMPLEX_UNIT_DIP)
            setViewLayoutMargin(R.id.frame,RemoteViews.MARGIN_LEFT,tile.x*size.width/w,COMPLEX_UNIT_DIP)
            setViewLayoutMargin(R.id.frame,RemoteViews.MARGIN_TOP,tile.y*size.height/h,COMPLEX_UNIT_DIP)
        }
        return segments.map { segment ->
            if(segment.frames.size==1) frame(segment.frames.single()) else RemoteViews(c.packageName,R.layout.motion).apply {
                setInt(R.id.flipper,"setFlipInterval",maxOf(500,plan.periodMs/plan.frames))
                segment.frames.forEach { addView(R.id.flipper,frame(it)) }
            }
        }
    }
}
