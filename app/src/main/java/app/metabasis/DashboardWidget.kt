package app.metabasis

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class DashboardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: manager.getAppWidgetIds(android.content.ComponentName(context, DashboardWidget::class.java))
            ids.forEach { update(context, manager, it) }
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val state = StepLedger.load(context)
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
        val tint = try {
            android.graphics.Color.parseColor(themeColor(state.themeId))
        } catch (_: Exception) { Color.rgb(255, 161, 51) }
        views.setInt(R.id.widget_status, "setBackgroundColor", tint)
        val hour = java.time.LocalTime.now().hour
        val salutation = when { hour < 5 -> "Rest well"; hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; hour < 22 -> "Good evening"; else -> "Good night" }
        views.setTextViewText(R.id.widget_name, "$salutation, ${state.userName.ifBlank { "Walker" }}")
        views.setTextViewText(R.id.widget_steps, "%,d".format(state.todaySteps))
        views.setTextViewText(R.id.widget_goal, "of %,d today".format(state.goalSteps))
        views.setTextViewText(R.id.widget_stage, "CYCLE ${state.cycle} · STAGE ${state.stage + 1} · ${(state.progress * 100).toInt()}%")
        views.setProgressBar(R.id.widget_progress, 100, (state.progress * 100).toInt(), false)
        val theme = when (state.themeId) {
            "lush" -> intArrayOf(Color.rgb(215,235,181), Color.rgb(163,212,106), Color.rgb(95,168,61), Color.rgb(46,125,50), Color.rgb(20,64,26))
            "shed" -> intArrayOf(Color.rgb(212,232,176), Color.rgb(242,201,76), Color.rgb(224,122,46), Color.rgb(192,57,43), Color.rgb(107,66,38))
            "cherry" -> intArrayOf(Color.rgb(255,217,227), Color.rgb(255,179,199), Color.rgb(240,98,146), Color.rgb(194,24,91), Color.rgb(123,30,63))
            "dusk" -> intArrayOf(Color.rgb(220,230,245), Color.rgb(127,168,224), Color.rgb(74,95,193), Color.rgb(106,63,160), Color.rgb(42,27,74))
            else -> intArrayOf(Color.rgb(211,215,218), Color.rgb(225,228,230), Color.rgb(244,244,240), Color.rgb(185,190,195), Color.rgb(232,234,235))
        }
        val colors = if (state.logoMode == "theme") theme else intArrayOf(Color.rgb(211,215,218), Color.rgb(225,228,230), Color.rgb(244,244,240), Color.rgb(185,190,195), Color.rgb(232,234,235))
        for (i in 0..4) views.setInt(context.resources.getIdentifier("widget_led_${i + 1}", "id", context.packageName), "setBackgroundColor", colors[i])
        val launch = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, launch)
        manager.updateAppWidget(id, views)
    }

    private fun themeColor(id: String) = when (id) {
        "lush" -> "#5FA83D"
        "shed" -> "#E07A2E"
        "cherry" -> "#F06292"
        "dusk" -> "#7FA8E0"
        else -> "#FFA133"
    }
}
