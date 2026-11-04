package dev.mwidget.runtime

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

abstract class RuntimeActivity : Activity() {
    protected lateinit var content: LinearLayout
    private lateinit var permission: TextView
    protected lateinit var status: TextView
    private var importing=false

    protected fun startUi(title: String) {
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(28),dp(24),dp(32)) }
        val scroll=ScrollView(this).apply { addView(content); setBackgroundColor(Color.rgb(245,247,245)) }
        scroll.setOnApplyWindowInsetsListener { v,insets ->
            val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(safe.left,safe.top,safe.right,safe.bottom)
            insets
        }
        setContentView(scroll)
        text("M  /  MUSIC WIDGET",12).apply {
            letterSpacing=0.16f
            setTextColor(Color.rgb(45,105,87))
            setTypeface(null,Typeface.BOLD)
        }
        text(title,32).setTypeface(null,Typeface.BOLD)
        text("내 취향을 담은 작은 플레이어",15)
        text("시작하기",20).setTypeface(null,Typeface.BOLD)
        permission=text("",15)
        button("알림 접근 설정 열기") {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .onFailure { status.text="기기 설정에서 알림 접근 메뉴를 열어 주세요." }
        }
        button(".mwidget 가져오기") {
            if(!importing) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT)
        }
        status=text("",14)
        importer.execute {
            val result=runCatching { ThemeStore.ensureDefault(applicationContext) }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    result.onFailure { status.text="기본 테마 오류: ${it.localizedMessage}" }
                    refreshOptions()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if(!::permission.isInitialized) return
        permission.text=if(Sessions.allowed(this)) "연결 준비 완료 · 음악 정보를 읽을 수 있어요" else
            "음악 제목과 재생 제어에 알림 접근이 필요합니다. 설정에서 Music Widget Runtime을 허용하세요."
        refreshOptions()
        MusicWidget.refresh(this)
    }

    @Deprecated("Legacy framework activity result API for a dependency-free picker")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        val uri=data?.data ?: return
        if(requestCode!=IMPORT || resultCode!=RESULT_OK) return
        importing=true; status.text="패키지 검증 중…"
        importer.execute {
            val result=runCatching {
                val input=contentResolver.openInputStream(uri) ?: error("파일을 열 수 없습니다")
                ThemeStore.importTheme(applicationContext,input)
            }
            if (result.isSuccess) MusicWidget.refresh(applicationContext)
            runOnUiThread {
                importing=false
                if(!isFinishing && !isDestroyed) {
                    status.text=result.fold({ "가져옴 · ${it.json.getString("name")}" },{ "가져오기 실패 · ${it.localizedMessage ?: "잘못된 패키지"}" })
                    refreshOptions(result.getOrNull()?.id)
                }
            }
        }
    }

    protected abstract fun refreshOptions(imported: String?=null)
    protected fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
    protected fun text(label: String,size: Int): TextView = TextView(this).apply {
        text=label; textSize=size.toFloat(); setTextColor(Color.rgb(27,43,36)); setPadding(0,dp(8),0,dp(8)); content.addView(this)
    }
    protected fun button(label: String,action: (View)->Unit) = Button(this).apply {
        text=label; styleButton(this); setOnClickListener(action); content.addView(this)
    }
    protected fun styleButton(view: Button) {
        view.isAllCaps=false
        view.textSize=16f
        view.gravity=android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
        view.setPadding(dp(20),dp(14),dp(20),dp(14))
        view.minHeight=dp(56)
        view.setTextColor(Color.rgb(245,255,249))
        view.backgroundTintList=ColorStateList.valueOf(Color.rgb(36,83,65))
        view.layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(4) }
    }
    protected fun spinner() = Spinner(this).also {
        it.minimumHeight=dp(56)
        it.setPadding(dp(12),dp(8),dp(12),dp(8))
        it.background=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(14).toFloat(); setStroke(dp(1),Color.rgb(215,225,218)) }
        content.addView(it,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    protected fun bind(spinner: Spinner,labels: List<String>,selected: Int) {
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
        if(labels.isNotEmpty()) spinner.setSelection(selected.coerceIn(0,labels.lastIndex))
    }
    companion object {
        private const val IMPORT=42
        private val importer=Executors.newSingleThreadExecutor()
    }
}

class MainActivity : RuntimeActivity() {
    private lateinit var widgets: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startUi("나만의 음악 공간")
        text("홈 화면을 길게 누르고 위젯 목록에서 Music Widget Runtime을 추가하세요.",16)
        text("내 위젯",22).setTypeface(null,Typeface.BOLD)
        widgets=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; content.addView(this) }
    }

    override fun refreshOptions(imported: String?) {
        if(!::widgets.isInitialized) return
        widgets.removeAllViews()
        val ids=MusicWidget.ids(this)
        if(ids.isEmpty()) widgets.addView(TextView(this).apply { text="아직 위젯이 없어요\n홈 화면에 첫 위젯을 추가해 보세요."; textSize=16f; setPadding(dp(16),dp(20),dp(16),dp(20)); setTextColor(Color.rgb(83,97,90)) })
        ids.forEach { id ->
            val theme=ThemeStore.prefs(this).getString("widget.$id.theme","nocturne")!!
            val name=runCatching { ThemeStore.get(this,theme).json.getString("name") }.getOrDefault(theme)
            widgets.addView(Button(this).apply {
                text="$name  ·  설정 열기"; styleButton(this)
                setOnClickListener { startActivity(Intent(this@MainActivity,ConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)) }
            })
        }
    }
}

class ConfigureActivity : RuntimeActivity() {
    private var widgetId=AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var themeSelector: Spinner
    private lateinit var playerSelector: Spinner
    private lateinit var save: Button
    private var themes=emptyList<Theme>()
    private var players=listOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val result=Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId)
        setResult(RESULT_CANCELED,result)
        if(widgetId !in MusicWidget.ids(this)) { finish(); return }
        startUi("위젯 설정")
        text("테마",18); themeSelector=spinner()
        text("플레이어",18); playerSelector=spinner()
        text("목록에 앱이 없으면 해당 앱에서 곡을 먼저 재생하세요.",14)
        save=button("이 위젯에 적용") {
            val theme=themes.getOrNull(themeSelector.selectedItemPosition) ?: return@button
            val player=players.getOrNull(playerSelector.selectedItemPosition)
            val prefs=ThemeStore.prefs(this)
            prefs.edit().putString("widget.$widgetId.theme",theme.id).putString("widget.$widgetId.player",player).apply()
            MusicWidget.refresh(this)
            setResult(RESULT_OK,result); finish()
        }
    }

    override fun refreshOptions(imported: String?) {
        if(!::save.isInitialized) return
        val prefs=ThemeStore.prefs(this)
        val selectedTheme=imported ?: themes.getOrNull(themeSelector.selectedItemPosition)?.id ?: prefs.getString("widget.$widgetId.theme","nocturne")
        val selectedPlayer=if(playerSelector.selectedItemPosition>=0) players.getOrNull(playerSelector.selectedItemPosition) else prefs.getString("widget.$widgetId.player",null)
        themes=ThemeStore.list(this)
        bind(themeSelector,themes.map { it.json.getString("name") },themes.indexOfFirst { it.id==selectedTheme })
        val active=Sessions.list(this).map { it.packageName }
        players=listOf(null)+(active+listOfNotNull(selectedPlayer)).distinct()
        bind(playerSelector,players.map { p -> if(p==null) "자동 · 재생 중인 앱 우선" else Sessions.label(this,p) + if(p in active) "" else " · 대기 중" },players.indexOf(selectedPlayer))
        save.isEnabled=themes.isNotEmpty()
    }
}
