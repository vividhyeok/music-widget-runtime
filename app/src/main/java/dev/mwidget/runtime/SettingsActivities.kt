package dev.mwidget.runtime

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.ViewTreeObserver
import android.widget.*
import java.util.concurrent.Executors

internal data class Catalog(val themes: List<Theme>,val players: List<Pair<String,String>>,val broken: Int)

abstract class RuntimeActivity: Activity() {
    protected lateinit var content: LinearLayout
    protected lateinit var status: TextView
    private lateinit var permission: TextView
    private lateinit var importButton: Button
    private var loadGeneration=0
    private var seenImport=0L
    private var active=false
    private var pendingImported: String?=null
    private lateinit var scroll: ScrollView
    private var restoredScroll=-1
    private val importObserver: (Imports.State)->Unit = { state ->
        importButton.isEnabled=!state.busy
        if(state.message.isNotEmpty()) status.text=state.message
        if(!state.busy && state.revision>seenImport) {
            seenImport=state.revision; refreshOptions(state.themeId)
        }
    }
    protected fun startUi(title: String,subtitle: String) {
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(24),dp(22),dp(32)) }
        scroll=ScrollView(this).apply { addView(content); setBackgroundColor(Color.rgb(245,247,245)); isFillViewport=true }
        scroll.setOnApplyWindowInsetsListener { v,insets -> val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()); v.setPadding(safe.left,safe.top,safe.right,safe.bottom); insets }
        setContentView(scroll)
        val bars=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(bars,bars)
        label(content,"M / MUSIC WIDGET",12).apply { letterSpacing=.14f; setTextColor(Color.rgb(42,100,75)) }
        label(content,title,30).setTypeface(null,Typeface.BOLD)
        label(content,subtitle,15)
        permission=label(content,"연결 상태 확인 중…",14)
        permission.minHeight=dp(48)
        permission.contentDescription="음악 연결 상태 · 눌러서 알림 접근 설정 열기"
        permission.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.onFailure { status.text="설정 앱에서 알림 접근 메뉴를 열어 주세요." }
        }
        importButton=button(content,"+ 테마 가져오기",true) {
            if(!Imports.state.busy) runCatching { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT) }
                .onFailure { status.text="파일 선택기를 열 수 없어요. 기기의 파일 앱을 확인해 주세요." }
        }
        status=label(content,"",14).apply { accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
    }
    override fun onStart() { super.onStart(); active=true; if(::importButton.isInitialized) Imports.observe(importObserver) }
    override fun onStop() { active=false; loadGeneration++; Imports.remove(importObserver); super.onStop() }
    override fun onResume() {
        super.onResume()
        if(!::permission.isInitialized) return
        permission.text=if(Sessions.allowed(this)) "● 음악 연결됨 · 설정 ›" else "○ 음악 연결 필요 · 눌러서 설정 ›"
        refreshOptions(); MusicWidget.refresh(this)
    }
    override fun onSaveInstanceState(out: Bundle) { out.putLong("seenImport",seenImport); if(::scroll.isInitialized) out.putInt("scrollY",scroll.scrollY); super.onSaveInstanceState(out) }
    protected fun restoreUi(state: Bundle?) { seenImport=state?.getLong("seenImport") ?: Imports.state.revision; restoredScroll=state?.getInt("scrollY",0) ?: -1 }
    @Deprecated("Framework file picker; import operation is process-scoped")
    override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(request!=IMPORT || result!=RESULT_OK) return
        val uri=data?.data
        if(uri==null) { status.text="선택한 파일을 읽을 수 없어요. 다시 선택해 주세요."; return }
        runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        Imports.start(applicationContext,uri)
    }
    protected abstract fun refreshOptions(imported: String?=null)
    internal fun loadCatalog(imported: String?,loaded: (Catalog,String?)->Unit) {
        if(imported!=null) pendingImported=imported
        val desired=pendingImported
        val token=++loadGeneration; val app=applicationContext
        io.execute {
            val result=runCatching {
                ThemeStore.ensureDefault(app)
                val themes=ThemeStore.list(app)
                val players=Sessions.list(app).map { it.packageName }.distinct().map { it to Sessions.label(app,it) }
                Catalog(themes,players,ThemeStore.prefs(app).all.keys.count { it.startsWith("theme.") }-themes.size)
            }
            main.post {
                if(!active || isFinishing || isDestroyed || token!=loadGeneration) return@post
                result.onSuccess {
                    val y=if(restoredScroll>=0) restoredScroll else scroll.scrollY
                    pendingImported=null; loaded(it,desired)
                    scroll.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() { scroll.viewTreeObserver.removeOnGlobalLayoutListener(this); scroll.scrollTo(0,y); restoredScroll=-1 }
                    })
                }.onFailure { status.text="목록을 불러오지 못했어요 · ${it.localizedMessage ?: "잠시 후 다시 시도해 주세요"}" }
            }
        }
    }
    protected fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    protected fun label(parent: LinearLayout,text: String,size: Int=15)=TextView(this).apply {
        this.text=text; textSize=size.toFloat(); setTextColor(Color.rgb(29,48,38)); setPadding(0,dp(8),0,dp(8)); parent.addView(this)
    }
    protected fun card(parent: LinearLayout)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(16))
        background=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(20).toFloat(); setStroke(dp(1),Color.rgb(221,229,222)) }
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) })
    }
    protected fun button(parent: LinearLayout,label: String,primary: Boolean=false,action: (View)->Unit)=Button(this).apply {
        text=label; isAllCaps=false; textSize=16f; minHeight=dp(52); setPadding(dp(16),dp(10),dp(16),dp(10))
        setTextColor(if(primary) Color.WHITE else Color.rgb(37,83,61))
        backgroundTintList=ColorStateList.valueOf(if(primary) Color.rgb(36,83,65) else Color.rgb(229,237,231))
        setOnClickListener(action); parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
    }
    protected fun preview(parent: LinearLayout,theme: Theme): ThemePreview {
        val image=ThemePreview(this); parent.addView(image,LinearLayout.LayoutParams(-1,-2))
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL; parent.addView(this) }
        val message=label(row,"",13).apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
        lateinit var retry: ImageButton
        fun show() { image.show(theme) { error ->
            row.visibility=if(error==null) View.GONE else View.VISIBLE
            message.text=if(image.failed) "미리보기를 불러오지 못했어요" else "미리보기 준비 중…"
            retry.visibility=if(image.failed) View.VISIBLE else View.GONE
        } }
        retry=icon(row,"미리보기 다시 불러오기",R.drawable.ic_refresh) { show() }
        show()
        return image
    }
    protected fun icon(parent: LinearLayout,label: String,resource: Int,action: (View)->Unit)=ImageButton(this).apply {
        contentDescription=label; setImageResource(resource); imageTintList=ColorStateList.valueOf(Color.rgb(37,83,61)); setPadding(dp(12),dp(12),dp(12),dp(12))
        val attrs=obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)); background=attrs.getDrawable(0); attrs.recycle()
        setOnClickListener(action); parent.addView(this,LinearLayout.LayoutParams(dp(48),dp(48)))
    }
    protected fun restoreDefault() {
        val app=applicationContext
        MusicWidget.worker.execute {
            val result=runCatching { ThemeStore.restoreDefault(app) }
            runOnUiThread { if(!isFinishing && !isDestroyed) { result.onFailure { status.text="기본 테마를 복원하지 못했어요" }; refreshOptions() } }
        }
    }
    companion object {
        private const val IMPORT=42
        private val io=Executors.newSingleThreadExecutor()
        private val main=Handler(Looper.getMainLooper())
    }
}

class MainActivity: RuntimeActivity() {
    private lateinit var widgets: LinearLayout
    private lateinit var gallery: LinearLayout
    private lateinit var galleryStatus: TextView
    private var themes=emptyList<Theme>()
    private var page=0
    private var deleting=false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); restoreUi(state); page=state?.getInt("page") ?: 0
        startUi("내 음악, 내 디자인","테마를 미리 보고 홈 화면에 적용하세요.")
        label(content,"내 홈 화면 위젯",21).setTypeface(null,Typeface.BOLD)
        widgets=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; content.addView(this) }
        label(content,"보관한 테마",21).setTypeface(null,Typeface.BOLD)
        galleryStatus=label(content,"테마를 불러오고 있어요…",14)
        label(content,"샘플 곡으로 표시해요. ＋로 추가하고 ⋮에서 적용·삭제할 수 있어요.",13)
        gallery=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; content.addView(this) }
        button(content,"목록 새로고침") { refreshOptions() }
        val settings=card(content)
        label(settings,"표시 설정",18).setTypeface(null,Typeface.BOLD)
        settings.addView(Switch(this).apply {
            text="단계별 움직임 허용"; minHeight=dp(48); isChecked=ThemeStore.prefs(this@MainActivity).getBoolean("motion",true)
            setOnCheckedChangeListener { _,checked -> ThemeStore.prefs(this@MainActivity).edit().putBoolean("motion",checked).apply(); MusicWidget.refresh(this@MainActivity) }
        })
        label(settings,"움직임은 테마와 런처에 따라 달라요. 절전 모드에서는 정적인 디자인으로 표시합니다.",13)
    }
    override fun onSaveInstanceState(out: Bundle) { out.putInt("page",page); super.onSaveInstanceState(out) }
    override fun refreshOptions(imported: String?) {
        if(!::gallery.isInitialized) return
        loadCatalog(imported) { catalog,newId ->
            themes=catalog.themes
            if(newId!=null) { page=(themes.indexOfFirst { it.id==newId }.coerceAtLeast(0))/4 }
            renderWidgets(); renderGallery()
            if(catalog.broken>0) status.text="읽을 수 없는 테마 ${catalog.broken}개가 있어요. 기존 설정은 보존했습니다. 원본 파일을 다시 가져와 복구할 수 있어요."
        }
    }
    private fun configure(id: Int,theme: String?=null) { startActivity(Intent(this,ConfigureActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).putExtra("suggestedTheme",theme)) }
    private fun renderWidgets() {
        widgets.removeAllViews(); val ids=MusicWidget.ids(this)
        if(ids.isEmpty()) label(widgets,"아직 홈 화면 위젯이 없어요. 아래에서 테마를 골라 추가해 보세요.")
        ids.forEach { id ->
            val selected=ThemeStore.prefs(this).getString("widget.$id.theme","nocturne")
            val name=themes.firstOrNull { it.id==selected }?.json?.getString("name") ?: "테마 확인 필요"
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL; widgets.addView(this) }
            label(row,name,16).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
            icon(row,"$name 위젯 설정",R.drawable.ic_settings) { configure(id) }
        }
    }
    private fun renderGallery() {
        gallery.removeAllViews(); page=page.coerceIn(0,(themes.size-1).coerceAtLeast(0)/4)
        galleryStatus.text=if(themes.isEmpty()) "보관한 테마가 없어요. .mwidget 파일을 가져와 주세요." else "${themes.size}개 테마 · ${page+1}/${(themes.size+3)/4}페이지"
        for(theme in themes.drop(page*4).take(4)) {
            val panel=card(gallery)
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL; panel.addView(this) }
            val name=theme.json.getString("name")
            label(row,name,18).apply { setTypeface(null,Typeface.BOLD); maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
            icon(row,"$name 홈 화면에 추가",R.drawable.ic_add) { pin(theme) }
            icon(row,"$name 더보기",R.drawable.ic_more) { anchor -> actions(theme,anchor) }
            preview(panel,theme)
        }
        if(themes.isEmpty()) button(gallery,"기본 테마 복원") { restoreDefault() }
        if(page>0) button(gallery,"이전 테마") { page--; renderGallery() }
        if((page+1)*4<themes.size) button(gallery,"다음 테마") { page++; renderGallery() }
    }
    private fun pin(theme: Theme) {
        val manager=AppWidgetManager.getInstance(this)
        ThemeStore.prefs(this).edit().putString("suggestedTheme",theme.id).apply()
        val pinned=runCatching { manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(MusicWidget.component(this),null,null) }.getOrDefault(false)
        if(!pinned) status.text="홈 화면을 길게 누른 뒤 위젯 → Music Widget Runtime에서 추가해 주세요."
    }
    private fun actions(theme: Theme,anchor: View) {
        if(deleting || Imports.state.busy) { status.text="테마 작업이 끝난 뒤 다시 시도해 주세요."; return }
        PopupMenu(this,anchor).apply {
            if(MusicWidget.ids(this@MainActivity).isNotEmpty()) menu.add(0,1,0,"기존 위젯에 적용").setIcon(R.drawable.ic_settings)
            menu.add(0,2,1,"테마 정보")
            menu.add(0,3,2,"테마 삭제").setIcon(R.drawable.ic_delete)
            setForceShowIcon(true)
            setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    1 -> {
                        val ids=MusicWidget.ids(this@MainActivity)
                        if(ids.size==1) configure(ids.single(),theme.id) else if(ids.isNotEmpty()) android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("적용할 위젯 선택").setItems(ids.map { id ->
                                val selected=ThemeStore.prefs(this@MainActivity).getString("widget.$id.theme","nocturne")
                                "${themes.firstOrNull { it.id==selected }?.json?.getString("name") ?: "위젯"} · $id"
                            }.toTypedArray()) { _,index -> configure(ids[index],theme.id) }.show()
                    }
                    2 -> android.app.AlertDialog.Builder(this@MainActivity).setTitle(theme.json.getString("name"))
                        .setMessage("${theme.width} × ${theme.height}\n테마 v${theme.json.getInt("version")}\n${ThemeStore.users(this@MainActivity,theme.id).size}개 위젯에서 사용 중")
                        .setPositiveButton("닫기",null).show()
                    3 -> confirmDelete(theme)
                }; true
            }; show()
        }
    }
    private fun confirmDelete(theme: Theme) {
        val count=ThemeStore.users(this,theme.id).size
        val dialog=android.app.AlertDialog.Builder(this).setNegativeButton("취소",null)
        if(count==0) {
            dialog.setTitle("${theme.json.getString("name")} 삭제")
                .setMessage("앱에 보관한 테마와 이전 버전을 삭제합니다. 원본 .mwidget 파일은 지우지 않습니다.")
                .setPositiveButton("삭제") { _,_ -> delete(theme,null,false) }.show()
        } else {
            val alternatives=themes.filter { it.id!=theme.id }.map { it.id to it.json.getString("name") }.toMutableList()
            if(alternatives.isEmpty() && theme.id!="nocturne") alternatives.add("nocturne" to "Nocturne · 기본 테마 복원")
            if(alternatives.isEmpty()) {
                dialog.setTitle("사용 중인 마지막 테마예요").setMessage("다른 테마를 먼저 가져와 적용하거나 홈 화면의 위젯을 제거한 뒤 삭제할 수 있어요.")
                    .setPositiveButton("확인",null).show(); return
            }
            var selected=0
            dialog.setTitle("${theme.json.getString("name")} 삭제 · 위젯 ${count}개 전환")
                .setSingleChoiceItems(alternatives.map { it.second }.toTypedArray(),0) { _,index -> selected=index }
                .setPositiveButton("전환하고 삭제") { _,_ ->
                    val replacement=alternatives[selected].first
                    delete(theme,replacement,themes.none { it.id==replacement })
                }.show()
        }
    }
    private fun delete(theme: Theme,replacement: String?,restore: Boolean) {
        if(deleting || Imports.state.busy) return
        deleting=true; status.text="테마를 삭제하고 있어요…"; val app=applicationContext
        MusicWidget.worker.execute {
            val result=runCatching { if(restore) ThemeStore.restoreDefault(app); ThemeStore.delete(app,theme.id,replacement) }
            if(result.isSuccess) MusicWidget.refresh(app)
            runOnUiThread {
                deleting=false
                if(!isFinishing && !isDestroyed) {
                    status.text=result.fold({ if(it) "테마를 삭제했어요" else "목록에서 삭제했지만 일부 파일은 정리하지 못했어요" },{ "삭제하지 못했어요 · ${it.localizedMessage}" })
                    refreshOptions()
                }
            }
        }
    }
}

class ConfigureActivity: RuntimeActivity() {
    private var widgetId=AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var themeSelector: Spinner
    private lateinit var playerSelector: Spinner
    private lateinit var previewPanel: LinearLayout
    private lateinit var save: Button
    private var themes=emptyList<Theme>()
    private var players=listOf<String?>(null)
    private var selectedTheme: String?=null
    private var selectedPlayer: String?=null
    private var binding=false
    private var initialFallback=false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); restoreUi(state)
        widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val result=Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId); setResult(RESULT_CANCELED,result)
        if(widgetId !in MusicWidget.ids(this)) { finish(); return }
        val prefs=ThemeStore.prefs(this)
        initialFallback=state?.getBoolean("initialFallback") ?: (!prefs.contains("widget.$widgetId.theme") && intent.getStringExtra("suggestedTheme")==null)
        selectedTheme=state?.getString("theme") ?: intent.getStringExtra("suggestedTheme") ?: prefs.getString("widget.$widgetId.theme",prefs.getString("suggestedTheme","nocturne"))
        selectedPlayer=if(state!=null) state.getString("player") else prefs.getString("widget.$widgetId.player",null)
        startUi("위젯 설정","미리 보고 적용하세요. 저장하기 전에는 홈 화면이 바뀌지 않아요.")
        label(content,"테마",18); themeSelector=spinner()
        label(content,"음악 플레이어",18); playerSelector=spinner()
        label(content,"앱이 목록에 없으면 해당 앱에서 음악을 재생해 주세요. 선택한 앱이 종료되어도 설정은 유지돼요.",13)
        previewPanel=card(content)
        save=button(content,"이 위젯에 적용",true) {
            if(widgetId !in MusicWidget.ids(this)) { status.text="이 위젯이 홈 화면에서 제거됐어요."; save.isEnabled=false; return@button }
            val theme=themes.firstOrNull { it.id==selectedTheme }
            if(theme==null) { status.text="적용할 테마를 선택해 주세요."; return@button }
            val ok=prefs.edit().putString("widget.$widgetId.theme",theme.id).putString("widget.$widgetId.player",selectedPlayer).commit()
            if(!ok) { status.text="설정을 저장하지 못했어요. 다시 시도해 주세요."; return@button }
            MusicWidget.refresh(this); setResult(RESULT_OK,result); finish()
        }
        save.isEnabled=false
        themeSelector.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                if(binding) return
                val next=themes.getOrNull(position)?.id ?: return
                if(next!=selectedTheme) { selectedTheme=next; updatePreview() }
            }
        }
        playerSelector.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) { if(!binding) selectedPlayer=players.getOrNull(position) }
        }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("theme",selectedTheme); out.putString("player",selectedPlayer); out.putBoolean("initialFallback",initialFallback); super.onSaveInstanceState(out) }
    private fun spinner()=Spinner(this).apply {
        minimumHeight=dp(56); setPadding(dp(10),dp(8),dp(10),dp(8)); content.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    override fun refreshOptions(imported: String?) {
        if(!::save.isInitialized) return
        loadCatalog(imported) { catalog,newId ->
            themes=catalog.themes
            if(newId!=null) selectedTheme=newId
            if(initialFallback && themes.none { it.id==selectedTheme }) selectedTheme=themes.firstOrNull()?.id
            if(selectedTheme!=null && themes.any { it.id==selectedTheme }) initialFallback=false
            binding=true
            val themeNames=themes.map { it.json.getString("name") }.toMutableList()
            val missing=selectedTheme!=null && themes.none { it.id==selectedTheme }
            if(missing) themeNames.add("선택한 테마를 읽을 수 없어요 · 다시 가져오기")
            themeSelector.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,themeNames)
            if(missing) themeSelector.setSelection(themeNames.lastIndex,false)
            else {
                if(selectedTheme==null) selectedTheme=themes.firstOrNull()?.id
                if(themes.isNotEmpty()) themeSelector.setSelection(themes.indexOfFirst { it.id==selectedTheme }.coerceAtLeast(0),false)
            }
            players=listOf(null)+(catalog.players.map { it.first }+listOfNotNull(selectedPlayer)).distinct()
            val labels=players.map { pkg -> if(pkg==null) "자동 · 재생 중인 앱 우선" else catalog.players.firstOrNull { it.first==pkg }?.second ?: "${Sessions.label(this,pkg)} · 대기 중" }
            playerSelector.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels)
            playerSelector.setSelection(players.indexOf(selectedPlayer).coerceAtLeast(0),false)
            binding=false; updatePreview()
        }
    }
    private fun updatePreview() {
        previewPanel.removeAllViews()
        val theme=themes.firstOrNull { it.id==selectedTheme }
        save.isEnabled=theme!=null
        if(theme==null) {
            label(previewPanel,"선택한 테마를 표시할 수 없어요. 다른 테마를 선택하거나 원본 파일을 다시 가져와 주세요.")
            if(themes.isEmpty()) button(previewPanel,"기본 테마 복원") { restoreDefault() }
            return
        }
        label(previewPanel,"적용할 디자인",18).setTypeface(null,Typeface.BOLD)
        preview(previewPanel,theme)
    }
}
