package dev.mwidget.runtime

import android.graphics.Matrix
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject

/** One coordinate system for Canvas, host overlays and attached hit targets. */
object Scene {
    fun nodes(array: JSONArray): List<JSONObject> = buildList {
        for (i in 0 until array.length()) {
            val node=array.getJSONObject(i); add(node)
            node.optJSONArray("children")?.let { addAll(nodes(it)) }
        }
    }

    fun local(node: JSONObject): Matrix {
        val w=node.getDouble("w").toFloat(); val h=node.getDouble("h").toFloat()
        val matrix=Matrix()
        val t=node.optJSONObject("transform")
        if(t!=null) {
            val px=t.optDouble("pivotX",0.5).toFloat()*w; val py=t.optDouble("pivotY",0.5).toFloat()*h
            t.optJSONArray("quad")?.let { q ->
                val dst=FloatArray(8) { i -> q.getDouble(i).toFloat()*if(i%2==0) w else h }
                require(matrix.setPolyToPoly(floatArrayOf(0f,0f,w,0f,w,h,0f,h),0,dst,0,4))
            }
            val skew=Matrix().apply { setSkew(t.optDouble("skewX",0.0).toFloat(),t.optDouble("skewY",0.0).toFloat(),px,py) }
            matrix.postConcat(skew)
            matrix.postScale(t.optDouble("scaleX",1.0).toFloat(),t.optDouble("scaleY",1.0).toFloat(),px,py)
            matrix.postRotate(t.optDouble("rotation",0.0).toFloat(),px,py)
        }
        matrix.postTranslate(node.getDouble("x").toFloat(),node.getDouble("y").toFloat())
        require(matrix.invert(Matrix())) { "변환 행렬을 역변환할 수 없음" }
        return matrix
    }

    fun bounds(theme: Theme): Map<String,RectF> = buildMap {
        fun walk(array: JSONArray,parent: Matrix) {
            for(i in 0 until array.length()) {
                val node=array.getJSONObject(i)
                val matrix=Matrix(parent).apply { preConcat(local(node)) }
                val box=RectF(0f,0f,node.getDouble("w").toFloat(),node.getDouble("h").toFloat())
                matrix.mapRect(box)
                if(node.has("id")) put(node.getString("id"),box)
                node.optJSONArray("children")?.let { walk(it,matrix) }
            }
        }
        walk(theme.json.getJSONArray("layers"),Matrix())
    }

    fun hit(theme: Theme,hit: JSONObject): RectF = if(hit.has("target")) {
        RectF(bounds(theme).getValue(hit.getString("target")))
    } else RectF(hit.getDouble("x").toFloat(),hit.getDouble("y").toFloat(),
        (hit.getDouble("x")+hit.getDouble("w")).toFloat(),(hit.getDouble("y")+hit.getDouble("h")).toFloat())

    fun visible(node: JSONObject,song: Song): Boolean = when(node.optString("visibleWhen","always")) {
        "playing" -> song.playing; "paused" -> !song.playing
        "hasArtwork" -> song.art!=null; "noArtwork" -> song.art==null; else -> true
    }
}
