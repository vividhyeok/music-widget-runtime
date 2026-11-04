package dev.mwidget.runtime

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.fonts.Font
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipFile

data class Theme(val dir: File, val json: JSONObject) {
    val width get() = json.getInt("width")
    val height get() = json.getInt("height")
    val id get() = json.getString("id")
}

object ThemeStore {
    const val MAX_ZIP = 16 * 1024 * 1024
    private const val MAX_FILE = 8 * 1024 * 1024
    private const val MAX_TOTAL = 24 * 1024 * 1024
    private val path = Regex("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")
    val actions = setOf("previous", "toggle", "next", "switch", "settings", "rewind", "forward", "stop")
    fun prefs(c: Context) = c.getSharedPreferences("runtime", Context.MODE_PRIVATE)
    private fun root(c: Context) = File(c.filesDir,"themes").apply { mkdirs() }
    fun safeName(s: String) = s.length <= 160 && path.matches(s) && s.split('/').none { it == "." || it == ".." }
    fun bounded(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf); if (n < 0) break
            require(out.size().toLong() + n <= limit) { "파일 크기 제한 초과" }
            out.write(buf,0,n)
        }
        return out.toByteArray()
    }
    private fun header(file: File, bytes: Int): ByteArray = ByteArray(bytes).also { prefix ->
        java.io.DataInputStream(file.inputStream()).use { it.readFully(prefix) }
    }

    @Synchronized fun importTheme(c: Context, input: InputStream): Theme {
        val bytes = input.use { bounded(it, MAX_ZIP) }
        require(root(c).walkTopDown().filter { it.isFile }.sumOf { it.length() } + MAX_TOTAL < 160L*1024*1024) { "테마 저장소 용량 제한" }
        val archive = File(c.cacheDir,"import-${UUID.randomUUID()}.zip")
        val stage = File(root(c),"bundle-${UUID.randomUUID()}").apply { mkdirs() }
        var published = false
        try {
            archive.writeBytes(bytes)
            ZipFile(archive).use { zip ->
                require(zip.size() in 1..64) { "파일 개수 제한" }
                var total = 0L; val seen = mutableSetOf<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement(); val name = e.name
                    require(safeName(name) && seen.add(name.lowercase(java.util.Locale.ROOT)) && !e.isDirectory) { "안전하지 않은 ZIP 경로" }
                    require(e.method == 0 || e.method == 8)
                    require(e.compressedSize in 0..MAX_ZIP.toLong() && e.size in 0..MAX_FILE.toLong() && e.size <= maxOf(1L,e.compressedSize)*200) { "압축 해제 제한" }
                    val data = zip.getInputStream(e).use { bounded(it,MAX_FILE) }
                    val checksum = CRC32().apply { update(data) }
                    require(data.size.toLong() == e.size && checksum.value == e.crc) { "손상된 ZIP 파일" }
                    total += data.size; require(total <= MAX_TOTAL)
                    val f = File(stage,name)
                    require(f.canonicalPath.startsWith(stage.canonicalPath + File.separator))
                    f.parentFile!!.mkdirs(); f.writeBytes(data)
                }
            }
            val theme = validate(stage)
            // Publish only the validated directory. Existing widget renders may still hold the previous revision.
            require(prefs(c).edit().putString("theme.${theme.id}",stage.name).commit()) { "설정 저장 실패" }
            published = true
            return theme
        } finally {
            archive.delete()
            if (!published) stage.deleteRecursively()
        }
    }

    fun list(c: Context): List<Theme> = prefs(c).all.keys.filter { it.startsWith("theme.") }.mapNotNull {
        runCatching { get(c,it.removePrefix("theme.")) }.getOrNull()
    }.sortedBy { it.json.getString("name") }

    fun get(c: Context, id: String): Theme {
        require(Regex("[a-z][a-z0-9-]{0,47}").matches(id))
        val dir = prefs(c).getString("theme.$id",null) ?: error("테마를 가져오세요")
        require(safeName(dir) && '/' !in dir)
        val file = File(root(c),dir)
        return Theme(file,parse(File(file,"manifest.json").readText()))
    }

    fun parse(text: String): JSONObject {
        require(text.toByteArray().size <= 128*1024)
        fun read(r: JsonReader, depth: Int): Any {
            require(depth < 16) { "JSON 중첩 제한" }
            return when(r.peek()) {
                JsonToken.BEGIN_OBJECT -> JSONObject().also { o ->
                    r.beginObject(); while(r.hasNext()) { val k=r.nextName(); require(!o.has(k)) { "JSON 중복 키" }; o.put(k,read(r,depth+1)) }; r.endObject()
                }
                JsonToken.BEGIN_ARRAY -> JSONArray().also { a -> r.beginArray(); while(r.hasNext()) a.put(read(r,depth+1)); r.endArray() }
                JsonToken.STRING -> r.nextString()
                JsonToken.NUMBER -> r.nextDouble().also { require(it.isFinite()) }
                JsonToken.BOOLEAN -> r.nextBoolean()
                JsonToken.NULL -> { r.nextNull(); JSONObject.NULL }
                else -> error("JSON 형식 오류")
            }
        }
        return JsonReader(StringReader(text)).use { r ->
            r.isLenient=false
            val result=read(r,0) as JSONObject
            require(r.peek()==JsonToken.END_DOCUMENT); result
        }
    }

    private fun keys(o: JSONObject, required: Set<String>, optional: Set<String> = emptySet()) {
        val actual=o.keys().asSequence().toSet()
        require(actual.containsAll(required) && (actual-required-optional).isEmpty()) { "스키마 키 오류" }
    }
    private fun number(o: JSONObject,k: String,min: Double,max: Double): Double {
        require(o.get(k) is Number)
        return o.getDouble(k).also { require(it.isFinite() && it in min..max) { "범위 오류: $k" } }
    }
    private fun string(o: JSONObject,k: String): String { require(o.get(k) is String); return o.getString(k) }

    fun validate(dir: File): Theme {
        val manifest=File(dir,"manifest.json"); require(manifest.length() in 1..128*1024L)
        val m=parse(manifest.readText(Charsets.UTF_8))
        keys(m,setOf("version","id","name","width","height","layers","touch"))
        require(number(m,"version",1.0,1.0)==1.0)
        require(Regex("[a-z][a-z0-9-]{0,47}").matches(string(m,"id")))
        require(string(m,"name").length in 1..80)
        val width=number(m,"width",240.0,1024.0); val height=number(m,"height",120.0,1024.0)
        require(width%1==0.0 && height%1==0.0)
        val used=mutableSetOf("manifest.json"); val rect=setOf("x","y","w","h")
        fun bounds(o: JSONObject) {
            val x=number(o,"x",0.0,1024.0); val y=number(o,"y",0.0,1024.0)
            val w=number(o,"w",0.001,1024.0); val h=number(o,"h",0.001,1024.0)
            require(x+w<=width && y+h<=height) { "레이어가 캔버스를 벗어남" }
        }
        val layers=m.getJSONArray("layers"); require(layers.length() in 1..32)
        for(i in 0 until layers.length()) {
            val l=layers.getJSONObject(i); bounds(l)
            val fields=when(string(l,"type")) {
                "progress" -> {
                    for (key in listOf("color","trackColor"))
                        require(Regex("#[0-9a-fA-F]{6}").matches(string(l,key)))
                    setOf("color","trackColor")
                }
                "image" -> setOf("src")
                "artwork" -> setOf("fallback")
                "text" -> {
                    require(string(l,"field") in setOf("title","artist","album","player","status","elapsed","duration"))
                    number(l,"size",6.0,160.0)
                    require(Regex("#[0-9a-fA-F]{6}").matches(string(l,"color")))
                    setOf("field","size","color","font")
                }
                "icon" -> { require(string(l,"action") in actions); setOf("action","src") }
                else -> error("지원하지 않는 레이어")
            }
            keys(l,rect+fields+"type",if(l.getString("type")=="icon") setOf("playingSrc") else emptySet())
            for(k in listOf("src","fallback","font","playingSrc")) if(l.has(k)) {
                val p=string(l,k); require(safeName(p)); require(p.endsWith(if(k=="font") ".ttf" else ".png") || k=="font" && p.endsWith(".otf"))
                require(File(dir,p).isFile) { "에셋 누락: $p" }; used.add(p)
            }
        }
        val touch=m.getJSONArray("touch"); require(touch.length() in 0..8)
        for(i in 0 until touch.length()) {
            val a=touch.getJSONObject(i); keys(a,rect+setOf("action","label")); bounds(a)
            require(string(a,"action") in actions && string(a,"label").length in 1..80)
            for(j in 0 until i) {
                val b=touch.getJSONObject(j)
                require(!(a.getDouble("x")<b.getDouble("x")+b.getDouble("w") && b.getDouble("x")<a.getDouble("x")+a.getDouble("w") && a.getDouble("y")<b.getDouble("y")+b.getDouble("h") && b.getDouble("y")<a.getDouble("y")+a.getDouble("h"))) { "터치 영역 중첩" }
            }
        }
        var pixels=0L
        dir.walkTopDown().filter { it.isFile }.forEach { f ->
            val p=f.relativeTo(dir).invariantSeparatorsPath
            require(p in used || p=="LICENSE.txt") { "참조하지 않는 파일: $p" }
            when(f.extension) {
                "png" -> {
                    val data=f.readBytes()
                    require(data.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
                    val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeByteArray(data,0,data.size,opts)
                    require(opts.outWidth in 1..2048 && opts.outHeight in 1..2048 && opts.outWidth.toLong()*opts.outHeight<=2097152)
                    pixels+=opts.outWidth.toLong()*opts.outHeight; require(pixels<=8388608)
                    val bitmap=BitmapFactory.decodeByteArray(data,0,data.size) ?: error("손상된 PNG"); bitmap.recycle()
                }
                "ttf","otf" -> {
                    require(f.length() in 4..2*1024*1024L)
                    val h=header(f,4)
                    require(h.contentEquals(byteArrayOf(0,1,0,0)) || h.contentEquals("OTTO".toByteArray()))
                    val data=f.readBytes()
                    val buffer=ByteBuffer.allocateDirect(data.size)
                    buffer.put(data).rewind()
                    Font.Builder(buffer).build()
                }
                else -> require(p=="manifest.json" || p=="LICENSE.txt")
            }
        }
        return Theme(dir,m)
    }

    @Synchronized fun ensureDefault(c: Context) {
        if(prefs(c).contains("theme.nocturne")) return
        c.assets.open("nocturne.mwidget").use { importTheme(c,it) }
    }
}
