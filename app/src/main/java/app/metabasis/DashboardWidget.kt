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
        views.setTextViewText(R.id.widget_name, if (state.userName.isBlank()) "METABASIS" else "${state.userName.uppercase()} · METABASIS")
        views.setTextViewText(R.id.widget_steps, "%,d".format(state.todaySteps))
        views.setTextViewText(R.id.widget_goal, "of %,d today".format(state.goalSteps))
        views.setTextViewText(R.id.widget_stage, "CYCLE ${state.cycle} · STAGE ${state.stage + 1} · ${(state.progress * 100).toInt()}%")
        views.setProgressBar(R.id.widget_progress, 100, (state.progress * 100).toInt(), false)
        views.setInt(R.id.widget_accent, "setBackgroundColor", tint)
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
