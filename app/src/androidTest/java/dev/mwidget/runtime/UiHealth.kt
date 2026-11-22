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
    fun run(i: Instrumentation) {
        val app=i.targetContext; val before=ThemeStore.prefs(app).all.toMap()
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
        val monitor=i.addMonitor(MainActivity::class.java.name,null,false)
        main { activity.recreate() }
        val recreated=monitor.waitForActivityWithTimeout(15000) ?: error("Activity did not recreate")
        i.removeMonitor(monitor)
        val second=await(recreated)
        i.uiAutomation.takeScreenshot().also { bitmap -> File(output,"recreated.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
        check(before==ThemeStore.prefs(app).all) { "Runtime preferences changed during read-only UI test" }
        File(output,"report.json").writeText(JSONObject().put("previewsBefore",first).put("previewsAfterRecreate",second).put("runtimePreferencesUnchanged",true).toString(2))
    }
}
