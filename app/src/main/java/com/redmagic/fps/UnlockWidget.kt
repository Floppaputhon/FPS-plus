package com.redmagic.fps

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class UnlockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)
    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UnlockWidget::class.java))
            val prefs = context.getSharedPreferences("state", Context.MODE_PRIVATE)
            // Older releases stored Russian labels. Migrate only the known values, otherwise show Unknown.
            val state = prefs.getString("widget_state", null) ?: when (prefs.getString("widget", null)) {
                "Включено" -> "on"
                "Выключено" -> "off"
                "Ошибка" -> "error"
                else -> "unknown"
            }
            val localized = LocaleSupport.wrap(context)
            val value = localized.getString(when (state) {
                "on" -> R.string.status_on
                "off" -> R.string.status_off
                "error" -> R.string.status_error
                else -> R.string.status_unknown
            })
            val token = prefs.getString("widget_token", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("widget_token", it).apply() }
            for (id in ids) {
                val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
                views.setTextViewText(android.R.id.text1, localized.getString(R.string.widget_text, value))
                views.setTextColor(android.R.id.text1, Color.WHITE)
                views.setInt(android.R.id.text1, "setBackgroundColor", Color.rgb(65, 0, 8))
                val intent = Intent(context, MainActivity::class.java).setAction("com.redmagic.fps.WIDGET_TOGGLE").putExtra("token", token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                views.setOnClickPendingIntent(android.R.id.text1, PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
