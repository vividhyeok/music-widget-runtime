package dev.mwidget.runtime

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask

/** Read-only UI health check, unlike the disposable-emulator transport smoke suite. */
object UiHealth {
    fun checkDeletion(i: Instrumentation) {
        val app=i.targetContext; val before=ThemeStore.prefs(app).all.toMap()
        val id="qa-delete-"+java.util.UUID.randomUUID().toString().take(12)
        val output=java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            java.util.zip.ZipInputStream(app.assets.open("nocturne.mwidget")).use { source ->
                while(true) {
                    val entry=source.nextEntry ?: break
                    var data=source.readBytes()
                    if(entry.name=="manifest.json") data=ThemeStore.parse(data.toString(Charsets.UTF_8)).put("id",id).toString().toByteArray()
                    zip.putNextEntry(java.util.zip.ZipEntry(entry.name)); zip.write(data); zip.closeEntry()
                }
            }
        }
        try {
            val a=ThemeStore.importTheme(app,output.toByteArray().inputStream())
            val b=ThemeStore.importTheme(app,output.toByteArray().inputStream())
            check(ThemeStore.delete(app,id))
            check(!a.dir.exists() && !b.dir.exists())
            check(ThemeStore.prefs(app).all==before)
        } finally {
            if(ThemeStore.prefs(app).contains("theme.$id")) ThemeStore.delete(app,id)
        }
    }
    fun run(i: Instrumentation) {
        val app=i.targetContext; val before=ThemeStore.prefs(app).all.toMap()
        check(!app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { "Unlock the phone before the visible UI check" }
        val output=File(app.filesDir,"qa-ui").apply { mkdirs() }
        fun <T> main(block: ()->T): T { val task=FutureTask(Callable(block)); i.runOnMainSync(task); return task.get() }
        fun all(view: View): List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList())
        fun await(activity: android.app.Activity): Int {
            val until=SystemClock.elapsedRealtime()+30000
            while(SystemClock.elapsedRealtime()<until) {
                val ready=main {
                    val previews=all(activity.window.decorView).filterIsInstance<ThemePreview>()
                    if(previews.isNotEmpty() && previews.all { !it.loading && (it.drawable is BitmapDrawable || it.failed) }) previews.count { it.drawable is BitmapDrawable } else 0
                }
                if(ready>0) return ready
                SystemClock.sleep(100)
            }
            error("No theme preview became visible")
        }
        val activity=i.startActivitySync(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val first=await(activity)
        i.uiAutomation.takeScreenshot().also { bitmap -> File(output,"main.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
        val more=main { all(activity.window.decorView).filterIsInstance<android.widget.ImageButton>().first { it.contentDescription.toString().endsWith("더보기") } }
        main { check(more.performClick()) }
        fun node(text: String): android.view.accessibility.AccessibilityNodeInfo {
            val until=SystemClock.elapsedRealtime()+5000
            while(SystemClock.elapsedRealtime()<until) {
                i.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()?.let { return it }
                SystemClock.sleep(100)
            }
            error("Menu item missing: $text")
        }
        var action: android.view.accessibility.AccessibilityNodeInfo?=node("테마 삭제")
        var clicked=false
        while(action!=null) {
            if(action.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) { clicked=true; break }
            action=action.parent
        }
        check(clicked) { "Delete menu row was not clickable" }
        node("취소")
        i.uiAutomation.takeScreenshot().also { bitmap -> File(output,"delete-confirmation.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
        i.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        val monitor=i.addMonitor(MainActivity::class.java.name,null,false)
        main { activity.recreate() }
        val recreated=monitor.waitForActivityWithTimeout(15000) ?: error("Activity did not recreate")
        i.removeMonitor(monitor)
        val second=await(recreated)
        i.uiAutomation.takeScreenshot().also { bitmap -> File(output,"recreated.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
        check(before==ThemeStore.prefs(app).all) { "Runtime preferences changed during read-only UI test" }
        File(output,"report.json").writeText(JSONObject().put("previewsBefore",first).put("previewsAfterRecreate",second).put("deleteMenuAndConfirmation",true).put("runtimePreferencesUnchanged",true).toString(2))
    }
}
