package dev.mwidget.runtime

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

/** Bounded host-side playback; never schedules per-frame widget updates. */
object Dynamics {
    data class Plan(val frames: Int=1,val periodMs: Int=12000,val reason: String="static")
    fun plan(theme: Theme,song: Song,enabled: Boolean=true): Plan {
        if(!enabled) return Plan(reason="disabled-or-power-save")
        val nodes=buildList<org.json.JSONObject> {
            fun walk(a: org.json.JSONArray) { for(n in 0 until a.length()) { val node=a.getJSONObject(n); if(Scene.visible(node,song)) { add(node); node.optJSONArray("children")?.let { walk(it) } } } }
            walk(theme.json.getJSONArray("layers"))
        }
        val active=nodes.mapNotNull { it.optJSONObject("animation") }.filter { it.getString("when")=="always" || song.playing }
        val implicit=song.playing && nodes.any { it.optString("overflow")=="marquee" || it.optString("mode")=="simulated" }
        if(active.isEmpty() && !implicit) return Plan()
        val periods=active.map { it.getInt("durationMs") }.toSet()+(if(implicit) setOf(12000) else emptySet())
        if(periods.size!=1) return Plan(reason="mixed-periods-use-static")
        val frames=if(implicit) 12 else active.maxOf { it.getInt("steps") }
        return Plan(minOf(frames,maxOf(2,periods.single()/500)),periods.single(),"host-frames-experimental")
    }
    fun enabled(c: Context): Boolean = ThemeStore.prefs(c).getBoolean("motion",true) &&
        !c.getSystemService(PowerManager::class.java).isPowerSaveMode &&
        Settings.Global.getFloat(c.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
}
