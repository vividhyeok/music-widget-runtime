package dev.mwidget.runtime

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.ImageView
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Process-scoped import operation survives Activity recreation without retaining an Activity. */
object Imports {
    data class State(val revision: Long=0,val busy: Boolean=false,val themeId: String?=null,val message: String="")
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val listeners=mutableSetOf<(State)->Unit>()
    var state=State(); private set
    fun observe(listener: (State)->Unit) { listeners.add(listener); listener(state) }
    fun remove(listener: (State)->Unit) { listeners.remove(listener) }
    private fun publish(value: State) { state=value; listeners.toList().forEach { it(value) } }
    fun start(context: Context,uri: android.net.Uri): Boolean {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(state.busy) return false
        val app=context.applicationContext
        publish(State(state.revision+1,true,message="테마를 안전하게 확인하고 있어요…"))
        worker.execute {
            val result=runCatching { val stream=app.contentResolver.openInputStream(uri) ?: error("선택한 파일을 열 수 없습니다"); ThemeStore.importTheme(app,stream) }
            runCatching { app.contentResolver.releasePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if(result.isSuccess) runCatching { MusicWidget.refresh(app) }
            main.post { publish(State(state.revision+1,false,result.getOrNull()?.id,result.fold({ "${it.json.getString("name")} · 가져오기 완료" },{ "가져오지 못했어요 · ${it.localizedMessage ?: "파일 형식을 확인해 주세요"}" }))) }
        }
        return true
    }
}

/** Fixed render bounds, generation checks and detach cleanup prevent blank/stale previews. */
class ThemePreview(context: Context,attrs: AttributeSet?=null): ImageView(context,attrs) {
    private var generation=0
    private var task: Future<*>?=null
    private var theme: Theme?=null
    private var song=sample()
    private var ready: ((String?)->Unit)?=null
    var loading=false; private set
    var failed=false; private set
    init { scaleType=ScaleType.FIT_CENTER; adjustViewBounds=false; setBackgroundColor(android.graphics.Color.rgb(228,235,230)) }
    override fun onMeasure(widthSpec: Int,heightSpec: Int) {
        val width=MeasureSpec.getSize(widthSpec).coerceAtLeast(1)
        val ratio=theme?.let { it.height.toFloat()/it.width } ?: .5f
        val height=(width*ratio).toInt().coerceIn((96*resources.displayMetrics.density).toInt(),(320*resources.displayMetrics.density).toInt())
        setMeasuredDimension(width,resolveSize(height,heightSpec))
    }
    fun show(value: Theme,track: Song=sample(),onReady: (String?)->Unit={}) {
        theme=value; song=track; ready=onReady; contentDescription="${value.json.getString("name")} 미리보기"
        requestLayout(); load()
    }
    fun clear() { generation++; task?.cancel(false); theme=null; loading=false; failed=false; setImageDrawable(null) }
    private fun load() {
        val value=theme ?: return
        val serial=++generation; task?.cancel(false); setImageDrawable(null)
        loading=true; failed=false
        ready?.invoke("미리보기를 만들고 있어요…")
        val track=song
        task=renderer.submit {
            val result=runCatching {
                val scale=minOf(720f/value.width,720f/value.height,kotlin.math.sqrt(350000f/value.width/value.height))
                Renderer.render(value,track,(value.width*scale).toInt().coerceAtLeast(1),(value.height*scale).toInt().coerceAtLeast(1))
            }
            main.post {
                val image=result.getOrNull()
                if(serial!=generation || !isAttachedToWindow) { image?.recycle(); return@post }
                loading=false; failed=image==null
                if(image!=null) { setImageBitmap(image); ready?.invoke(null) }
                else { setImageDrawable(null); ready?.invoke("미리보기를 만들지 못했어요. 다시 시도하거나 원본 테마를 다시 가져와 주세요.") }
            }
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if(theme!=null && drawable==null) load() }
    override fun onDetachedFromWindow() { generation++; task?.cancel(false); loading=false; setImageDrawable(null); super.onDetachedFromWindow() }
    companion object {
        private val renderer=Executors.newFixedThreadPool(2)
        private val main=Handler(Looper.getMainLooper())
        fun sample()=Song(title="달빛이 머무는 밤",artist="미리보기 아티스트",album="Music Widget",player="음악 플레이어",status="일시 정지",position=84000,duration=222000)
    }
}
