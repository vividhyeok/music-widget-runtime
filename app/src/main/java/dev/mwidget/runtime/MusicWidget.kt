package dev.mwidget.runtime

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.SizeF
import android.util.TypedValue.COMPLEX_UNIT_DIP
import android.view.View
import android.widget.RemoteViews
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MusicWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val manager=AppWidgetManager.getInstance(context)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val ids = when(action) {
            AppWidgetManager.ACTION_APPWIDGET_DELETED,AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,COMMAND -> intArrayOf(id)
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: ids(context)
            Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED -> ids(context)
            else -> { super.onReceive(context,intent); return }
        }
        val pending=goAsync()
        worker.execute {
            try {
                val prefs=ThemeStore.prefs(context)
                if(action==AppWidgetManager.ACTION_APPWIDGET_DELETED) {
                    prefs.edit().remove("widget.$id.theme").remove("widget.$id.player").apply()
                    return@execute
                }
                val valid=ids(context).toSet()
                if (action == COMMAND && id in valid) {
                    val message=command(context,id,intent)
                    update(context,manager,id,message)
                } else ids.filter { it in valid }.forEach { update(context,manager,it) }
            } catch(e: Exception) {
                Log.w(TAG,"Widget broadcast failed",e)
            } finally { pending.finish() }
        }
    }

    companion object {
        private const val TAG="MusicWidgetRuntime"
        private const val COMMAND="dev.mwidget.runtime.COMMAND"
        val worker = Executors.newSingleThreadExecutor()
        fun component(c: Context) = ComponentName(c,MusicWidget::class.java)
        fun ids(c: Context): IntArray = AppWidgetManager.getInstance(c).getAppWidgetIds(component(c))
        fun refresh(c: Context) {
            val all=ids(c)
            if(all.isNotEmpty()) c.sendBroadcast(Intent(c,MusicWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,all))
        }

        private fun command(c: Context,id: Int,intent: Intent): String? = try {
            val action=intent.getStringExtra("command")
            val prefs=ThemeStore.prefs(c)
            val current=prefs.getString("widget.$id.player",null)
            val sessions=Sessions.list(c)
            if(action=="switch") {
                val choices=listOf(null)+sessions.map { it.packageName }
                val next=choices[(choices.indexOf(current)+1)%choices.size]
                prefs.edit().putString("widget.$id.player",next).apply()
                if(sessions.isEmpty()) "전환할 활성 플레이어가 없습니다" else null
            } else if(action in setOf("previous","toggle","next")) {
                val displayed=intent.getStringExtra("player")
                val controller=Sessions.pick(sessions,current)
                when {
                    !Sessions.allowed(c) -> "설정에서 알림 접근을 허용하세요"
                    controller == null -> "활성 세션이 없습니다"
                    controller.packageName != displayed -> "플레이어가 바뀌었습니다. 다시 눌러 주세요"
                    !Sessions.send(controller,action!!) -> "플레이어가 이 동작을 지원하지 않습니다"
                    else -> null
                }
            } else null
        } catch (e: Exception) { Log.w(TAG,"Transport failed",e); "플레이어 제어 실패" }

        fun update(c: Context,manager: AppWidgetManager,id: Int,message: String?=null) {
            try {
                ThemeStore.ensureDefault(c)
                val prefs=ThemeStore.prefs(c)
                val theme=ThemeStore.get(c,prefs.getString("widget.$id.theme","nocturne")!!)
                val selected=prefs.getString("widget.$id.player",null)
                val controller=Sessions.pick(Sessions.list(c),selected)
                val song=Sessions.song(c,controller,selected)
                val options=manager.getAppWidgetOptions(id)
                @Suppress("DEPRECATION")
                val sizes=options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
                    .filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }.distinct()
                val player=controller?.packageName
                val views=if(sizes.size in 1..3) RemoteViews(sizes.associateWith { size ->
                    render(c,id,theme,song,player,size,message)
                }) else {
                    val w=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,280).coerceAtLeast(1)
                    val h=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,140).coerceAtLeast(1)
                    render(c,id,theme,song,player,SizeF(w.toFloat(),h.toFloat()),message)
                }
                manager.updateAppWidget(id,views)
            } catch(e: Exception) {
                Log.w(TAG,"Unable to render widget $id",e)
                runCatching { manager.updateAppWidget(id,RemoteViews(c.packageName,R.layout.widget).apply {
                    setViewVisibility(R.id.error,View.VISIBLE)
                    setTextViewText(R.id.error,"테마를 표시할 수 없습니다 · 설정 열기")
                    setOnClickPendingIntent(R.id.error,settings(c,id))
                    setOnClickPendingIntent(R.id.canvas,settings(c,id))
                }) }
            }
        }

        internal fun render(c: Context,id: Int,theme: Theme,song: Song,player: String?,size: SizeF,message: String?): RemoteViews {
            val density=c.resources.displayMetrics.density
            val scale=minOf(density, 1080f/maxOf(size.width,size.height), sqrt(400000f/size.width/size.height))
            val w=(size.width*scale).roundToInt().coerceAtLeast(1)
            val h=(size.height*scale).roundToInt().coerceAtLeast(1)
            val geometry=Geometry.fit(size.width,size.height,theme.width,theme.height)
            return RemoteViews(c.packageName,R.layout.widget).apply {
                setImageViewBitmap(R.id.canvas,Renderer.render(theme,song,w,h))
                setContentDescription(R.id.canvas,"${song.title}, ${song.artist}, ${song.status}")
                setOnClickPendingIntent(R.id.canvas,settings(c,id))
                val touch=theme.json.getJSONArray("touch")
                for (i in 0 until touch.length()) {
                    val hit=touch.getJSONObject(i)
                    val action=hit.getString("action")
                    val label=if(action=="toggle") { if(song.playing) "일시 정지" else "재생" } else hit.getString("label")
                    val click=if(action=="settings") settings(c,id) else PendingIntent.getBroadcast(c,0,
                        Intent(c,MusicWidget::class.java).setAction(COMMAND)
                            .setData(Uri.parse("mwidget://widget/$id/$action"))
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("command",action).putExtra("player",player),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    addView(R.id.hits,RemoteViews(c.packageName,R.layout.touch).apply {
                        setViewLayoutWidth(R.id.touch,hit.getDouble("w").toFloat()*geometry.scale,COMPLEX_UNIT_DIP)
                        setViewLayoutHeight(R.id.touch,hit.getDouble("h").toFloat()*geometry.scale,COMPLEX_UNIT_DIP)
                        setViewLayoutMargin(R.id.touch,RemoteViews.MARGIN_LEFT,geometry.left+hit.getDouble("x").toFloat()*geometry.scale,COMPLEX_UNIT_DIP)
                        setViewLayoutMargin(R.id.touch,RemoteViews.MARGIN_TOP,geometry.top+hit.getDouble("y").toFloat()*geometry.scale,COMPLEX_UNIT_DIP)
                        setContentDescription(R.id.touch,label)
                        setOnClickPendingIntent(R.id.touch,click)
                    })
                }
                if(message!=null) {
                    setViewVisibility(R.id.error,View.VISIBLE)
                    setTextViewText(R.id.error,message)
                    setOnClickPendingIntent(R.id.error,settings(c,id))
                }
            }
        }

        private fun settings(c: Context,id: Int): PendingIntent = PendingIntent.getActivity(c,id,
            Intent(c,ConfigureActivity::class.java).setData(Uri.parse("mwidget://widget/$id/settings"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
