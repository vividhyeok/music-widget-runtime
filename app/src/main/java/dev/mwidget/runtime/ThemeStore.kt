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

    fun users(c: Context,id: String): List<Int> {
        val p=prefs(c)
        val stored=p.all.filter { (key,value) -> Regex("widget\\.[0-9]+\\.theme").matches(key) && value==id }
            .keys.map { it.split('.')[1].toInt() }
        val defaults=MusicWidget.ids(c).filter { p.getString("widget.$it.theme","nocturne")==id }
        return (stored+defaults).distinct().sorted()
    }

    /** Unpublish and reassign atomically before removing this theme's private revisions. */
    @Synchronized fun delete(c: Context,id: String,replacement: String?=null): Boolean {
        require(Regex("[a-z][a-z0-9-]{0,47}").matches(id))
        val p=prefs(c); val current=p.getString("theme.$id",null) ?: error("이미 삭제된 테마입니다")
        val affected=users(c,id)
        if(affected.isNotEmpty()) {
            require(replacement!=null && replacement!=id) { "사용 중인 위젯의 대체 테마를 선택하세요" }
            validate(get(c,replacement).dir)
        }
        val edit=p.edit().remove("theme.$id")
        affected.forEach { edit.putString("widget.$it.theme",replacement) }
        if(p.getString("suggestedTheme",null)==id) edit.remove("suggestedTheme")
        if(id=="nocturne") edit.putBoolean("default.dismissed",true)
        require(edit.commit()) { "테마 삭제 설정을 저장하지 못했습니다" }
        val retained=p.all.filterKeys { it.startsWith("theme.") }.values.filterIsInstance<String>().toSet()
        val base=root(c).canonicalFile
        var cleaned=true
        base.listFiles().orEmpty().filter { it.isDirectory && it.canonicalFile.parentFile==base && it.name !in retained }.forEach { dir ->
            val belongs=dir.name==current || runCatching {
                val manifest=File(dir,"manifest.json")
                manifest.length() in 1..128*1024L && parse(manifest.readText(Charsets.UTF_8)).getString("id")==id
            }.getOrDefault(false)
            if(belongs && !runCatching { dir.deleteRecursively() }.getOrDefault(false)) cleaned=false
        }
        return cleaned
    }

    @Synchronized fun restoreDefault(c: Context) {
        if(!prefs(c).contains("theme.nocturne")) c.assets.open("nocturne.mwidget").use { importTheme(c,it) }
        require(prefs(c).edit().remove("default.dismissed").commit())
    }

    fun get(c: Context, id: String): Theme {
        require(Regex("[a-z][a-z0-9-]{0,47}").matches(id))
        val dir = prefs(c).getString("theme.$id",null) ?: error("테마를 가져오세요")
        require(safeName(dir) && '/' !in dir)
        val file = File(root(c),dir)
        val manifest=File(file,"manifest.json")
        require(manifest.length() in 1..128*1024L) { "테마 설명 파일이 없거나 너무 큽니다" }
        val theme=Theme(file,parse(manifest.readText(Charsets.UTF_8)))
        require(theme.id==id && theme.width in 240..1024 && theme.height in 120..1024)
        require(theme.json.getString("name").length in 1..80 && theme.json.getInt("version") in 1..2)
        theme.json.getJSONArray("layers"); theme.json.getJSONArray("touch")
        return theme
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
        val version=number(m,"version",1.0,2.0); require(version==1.0 || version==2.0)
        val v2=version==2.0
        keys(m,setOf("version","id","name","width","height","layers","touch"),if(v2) setOf("fonts") else emptySet())
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
        val rootLayers=m.getJSONArray("layers"); require(rootLayers.length() in 1..if(v2) 64 else 32)
        val layers=Scene.nodes(rootLayers); require(layers.size<=if(v2) 64 else 32)
        val ids=mutableSetOf<String>()
        val common=setOf("id","opacity","visibleWhen","transform","mask","maskSrc","radius","animation")
        for(l in layers) {
            number(l,"x",0.0,1024.0); number(l,"y",0.0,1024.0)
            number(l,"w",0.001,1024.0); number(l,"h",0.001,1024.0)
            if(!v2) bounds(l)
            if(l.has("id")) require(Regex("[a-z][a-z0-9-]{0,47}").matches(string(l,"id")) && ids.add(l.getString("id")))
            if(l.has("opacity")) number(l,"opacity",0.0,1.0)
            if(l.has("radius")) number(l,"radius",0.0,1024.0)
            if(l.has("visibleWhen")) require(string(l,"visibleWhen") in setOf("always","playing","paused","hasArtwork","noArtwork"))
            if(l.has("mask")) require(string(l,"mask") in setOf("none","circle","rounded"))
            for(k in listOf("transform","animation","gradient")) if(l.has(k)) require(l.get(k) is JSONObject)
            l.optJSONObject("transform")?.let { t ->
                keys(t,emptySet(),setOf("rotation","scaleX","scaleY","pivotX","pivotY","skewX","skewY","quad"))
                for(k in listOf("rotation","scaleX","scaleY","pivotX","pivotY","skewX","skewY")) if(t.has(k)) {
                    val range=when(k) { "scaleX","scaleY" -> 0.01..4.0; "pivotX","pivotY" -> 0.0..1.0; "skewX","skewY" -> -1.0..1.0; else -> -360.0..360.0 }
                    number(t,k,range.start,range.endInclusive)
                }
                if(t.has("quad")) {
                    val q=t.getJSONArray("quad"); require(q.length()==8)
                    val p=DoubleArray(8) { n -> require(q.get(n) is Number); q.getDouble(n).also { require(it.isFinite() && it in 0.0..1.0) } }
                    for(n in 0..3) { val a=n*2; val b=(n+1)%4*2; val c=(n+2)%4*2
                        require((p[b]-p[a])*(p[c+1]-p[b+1])-(p[b+1]-p[a+1])*(p[c]-p[b])>0.0001) { "원근 사각형은 시계 방향 볼록 도형이어야 함" }
                    }
                }
            }
            l.optJSONObject("animation")?.let { a ->
                keys(a,setOf("kind","durationMs","steps","when","fallback"),setOf("from","to","dx","dy","sources"))
                require(string(a,"kind") in setOf("rotate","translate","pulse","frames"))
                require(number(a,"durationMs",1000.0,60000.0)%1==0.0 && number(a,"steps",2.0,12.0)%1==0.0)
                require(string(a,"when") in setOf("playing","always") && string(a,"fallback")=="static")
                for(k in listOf("from","to","dx","dy")) if(a.has(k)) number(a,k,if(k in listOf("dx","dy")) -1024.0 else -360.0,if(k in listOf("dx","dy")) 1024.0 else 360.0)
                if(a.getString("kind")=="pulse") { if(a.has("from")) number(a,"from",0.0,1.0); if(a.has("to")) number(a,"to",0.0,1.0) }
                if(a.getString("kind")=="frames") require(l.getString("type") in setOf("image","artwork","icon") && a.getJSONArray("sources").length()==a.getInt("steps"))
                else require(!a.has("sources"))
            }
            Scene.local(l)
            val fields=when(string(l,"type")) {
                "group" -> { require(v2); setOf("children") }
                "shape" -> {
                    require(v2 && string(l,"shape") in setOf("rect","ellipse","polygon"))
                    require(Regex("#[0-9a-fA-F]{6}").matches(string(l,"color")))
                    if(l.has("strokeColor")) require(Regex("#[0-9a-fA-F]{6}").matches(string(l,"strokeColor")))
                    if(l.has("strokeWidth")) number(l,"strokeWidth",0.0,128.0)
                    l.optJSONObject("gradient")?.let { g -> keys(g,setOf("endColor","angle")); require(Regex("#[0-9a-fA-F]{6}").matches(string(g,"endColor"))); number(g,"angle",-360.0,360.0) }
                    if(l.getString("shape")=="polygon") require(l.has("points"))
                    if(l.has("points")) { val p=l.getJSONArray("points"); require(p.length() in 6..128 && p.length()%2==0); for(n in 0 until p.length()) require(p.get(n) is Number && p.getDouble(n).isFinite() && p.getDouble(n) in 0.0..1.0) }
                    setOf("shape","color")
                }
                "equalizer" -> { require(v2 && string(l,"mode") in setOf("static","simulated")); require(Regex("#[0-9a-fA-F]{6}").matches(string(l,"color"))); require(number(l,"bars",3.0,48.0)%1==0.0); if(l.has("gap")) number(l,"gap",0.0,1.0); if(l.has("seed")) require(number(l,"seed",0.0,65535.0)%1==0.0); setOf("mode","color","bars") }
                "progress" -> {
                    for (key in listOf("color","trackColor"))
                        require(Regex("#[0-9a-fA-F]{6}").matches(string(l,key)))
                    if(l.has("radius")) number(l,"radius",0.0,1024.0)
                    if(l.has("showFill")) require(l.get("showFill") is Boolean)
                    require(l.has("thumbSrc")==l.has("thumbWidth"))
                    if(l.has("thumbWidth")) number(l,"thumbWidth",0.001,l.getDouble("w"))
                    if(l.has("direction")) require(string(l,"direction") in setOf("horizontal","vertical","circular"))
                    if(l.has("strokeWidth")) number(l,"strokeWidth",0.001,128.0)
                    if(l.has("startAngle")) number(l,"startAngle",-360.0,360.0)
                    if(l.has("sweepAngle")) number(l,"sweepAngle",1.0,360.0)
                    if(l.optString("direction")=="circular") require(listOf("trackSrc","fillSrc","thumbSrc","thumbWidth").none { l.has(it) })
                    setOf("color","trackColor")
                }
                "image" -> setOf("src")
                "artwork" -> setOf("fallback")
                "text" -> {
                    require(string(l,"field") in setOf("title","artist","album","player","status","elapsed","duration") || v2 && string(l,"field")=="literal")
                    if(l.optString("field")=="literal") require(string(l,"text").length<=2048)
                    if(l.has("text")) require(string(l,"text").length<=2048)
                    if(l.has("align")) require(string(l,"align") in setOf("left","center","right"))
                    if(l.has("valign")) require(string(l,"valign") in setOf("top","center","bottom"))
                    if(l.has("overflow")) require(string(l,"overflow") in setOf("ellipsis","clip","wrap","marquee"))
                    if(l.has("maxLines")) require(number(l,"maxLines",1.0,8.0)%1==0.0)
                    if(l.has("lineSpacing")) number(l,"lineSpacing",0.8,2.0)
                    if(l.has("letterSpacing")) number(l,"letterSpacing",-0.1,1.0)
                    number(l,"size",6.0,160.0)
                    require(Regex("#[0-9a-fA-F]{6}").matches(string(l,"color")))
                    setOf("field","size","color","font")
                }
                "icon" -> { require(string(l,"action") in actions); setOf("action","src") }
                else -> error("지원하지 않는 레이어")
            }
            val extra=when(l.getString("type")) { "icon" -> setOf("playingSrc","fit"); "image","artwork" -> setOf("fit"); "text" -> setOf("text","align","valign","overflow","maxLines","lineSpacing","letterSpacing"); "shape" -> setOf("strokeColor","strokeWidth","gradient","points"); "equalizer" -> setOf("gap","seed"); "progress" -> setOf("trackSrc","fillSrc","thumbSrc","thumbWidth","radius","showFill")+(if(v2) setOf("direction","strokeWidth","startAngle","sweepAngle") else emptySet()); else -> emptySet() }
            val legacy=when(l.getString("type")) { "icon" -> setOf("playingSrc"); "progress" -> extra; else -> emptySet() }
            keys(l,rect+fields+"type",if(v2) extra+common else legacy)
            if(l.has("fit")) require(string(l,"fit") in setOf("crop","contain","stretch"))
            val assets=listOf("src","fallback","font","playingSrc","trackSrc","fillSrc","thumbSrc","maskSrc").filter { l.has(it) }.map { it to string(l,it) }.toMutableList()
            l.optJSONObject("animation")?.optJSONArray("sources")?.let { a -> for(n in 0 until a.length()) assets.add("src" to a.getString(n)) }
            for((k,p) in assets) {
                require(safeName(p)); require(p.endsWith(if(k=="font") ".ttf" else ".png") || k=="font" && p.endsWith(".otf"))
                require(File(dir,p).isFile) { "에셋 누락: $p" }; used.add(p)
            }
        }
        fun groups(a: JSONArray,depth: Int) { require(depth<=4); for(n in 0 until a.length()) a.getJSONObject(n).optJSONArray("children")?.let { require(it.length() in 1..64); groups(it,depth+1) } }
        groups(rootLayers,0)
        if(v2) {
            val fonts=m.optJSONArray("fonts") ?: JSONArray(); require(fonts.length()<=16)
            val declared=mutableSetOf<String>()
            for(n in 0 until fonts.length()) { val f=fonts.getJSONObject(n); keys(f,setOf("file","noonnu","source","license"))
                require(declared.add(string(f,"file")) && Regex("https://noonnu\\.cc/font_page/[0-9]+").matches(string(f,"noonnu")) && string(f,"source").startsWith("https://") && string(f,"license")=="LICENSE.txt") }
            require(declared==layers.filter { it.getString("type")=="text" }.map { it.getString("font") }.toSet()) { "눈누 폰트 출처를 명시하세요" }
            if(declared.isNotEmpty()) require(File(dir,"LICENSE.txt").length()>0)
        }
        val touch=m.getJSONArray("touch"); require(touch.length() in 0..8)
        for(i in 0 until touch.length()) {
            val a=touch.getJSONObject(i)
            if(v2 && a.has("target")) { keys(a,setOf("target","action","label")); require(string(a,"target") in ids) }
            else { keys(a,rect+setOf("action","label")); bounds(a) }
            val theme=Theme(dir,m); val box=Scene.hit(theme,a)
            require(box.left>=0 && box.top>=0 && box.right<=width && box.bottom<=height && box.width()>0 && box.height()>0) { "터치 영역이 캔버스를 벗어남" }
            if(a.has("target")) {
                fun stable(array: JSONArray,inherited: Boolean) { for(n in 0 until array.length()) { val l=array.getJSONObject(n); val changing=inherited || l.has("animation") || l.optString("visibleWhen","always")!="always"; if(l.optString("id")==a.getString("target")) require(!changing) { "움직이거나 숨겨지는 요소에는 고정 touch 좌표를 사용하세요" }; l.optJSONArray("children")?.let { stable(it,changing) } } }
                stable(rootLayers,false)
            }
            require(string(a,"action") in actions && string(a,"label").length in 1..80)
            for(j in 0 until i) {
                require(!android.graphics.RectF.intersects(box,Scene.hit(theme,touch.getJSONObject(j)))) { "터치 영역 중첩" }
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
                    require(f.length() in 4..(if(v2) 8 else 2)*1024*1024L)
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
        if(prefs(c).contains("theme.nocturne") || prefs(c).getBoolean("default.dismissed",false)) return
        c.assets.open("nocturne.mwidget").use { importTheme(c,it) }
    }
}
