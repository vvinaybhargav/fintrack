package com.household.finance.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.household.finance.MainActivity
import com.household.finance.data.Account
import java.util.Locale

private val KEY_SURPLUS = stringPreferencesKey("surplus")
private val KEY_ACCOUNTS = stringPreferencesKey("accounts") // "NAME:balance|NAME:balance"
val START_ROUTE_KEY = ActionParameters.Key<String>("start_route")

// Widget is intentionally single-themed (matches the app's dark glass look regardless of system theme).
private val WidgetBackground = ColorProvider(Color(0xFF12141C))
private val WidgetTextPrimary = ColorProvider(Color(0xFFF3F3F7))
private val WidgetTextSecondary = ColorProvider(Color(0xFFA5A8B8))
private val WidgetAccent = ColorProvider(Color(0xFF8B7CF6))

private fun formatInrShort(value: Double): String {
    val absVal = Math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absVal >= 100000 -> "$sign₹" + String.format(Locale.US, "%.1fL", absVal / 100000.0)
        absVal >= 1000 -> "$sign₹" + String.format(Locale.US, "%.1fk", absVal / 1000.0)
        else -> "$sign₹" + absVal.toInt()
    }
}

class HouseholdWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    private fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val surplusText = prefs[KEY_SURPLUS] ?: "—"
        val accountsRaw = prefs[KEY_ACCOUNTS] ?: ""
        val accounts = accountsRaw.split("|").filter { it.isNotBlank() }.mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toDoubleOrNull() ?: 0.0) else null
        }

        Column(
            modifier = GlanceModifier.fillMaxSize().background(WidgetBackground).padding(12.dp)
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    "Household Finance",
                    style = TextStyle(color = WidgetTextSecondary, fontSize = 11.sp),
                    modifier = GlanceModifier.fillMaxWidth()
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Text("Surplus: $surplusText/mo", style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp))
            Spacer(GlanceModifier.height(8.dp))
            accounts.take(3).forEach { (name, balance) ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(name, style = TextStyle(color = WidgetTextPrimary, fontSize = 13.sp), modifier = GlanceModifier.width(90.dp))
                    Text(formatInrShort(balance), style = TextStyle(color = WidgetTextPrimary, fontSize = 13.sp))
                }
            }
            if (accounts.isEmpty()) {
                Text("Open the app to see balances", style = TextStyle(color = WidgetTextSecondary, fontSize = 11.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                "+ Add entry",
                style = TextStyle(color = WidgetAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(
                    actionStartActivity<MainActivity>(parameters = actionParametersOf(START_ROUTE_KEY to "add"))
                )
            )
        }
    }
}

class HouseholdWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HouseholdWidget()
}

/** Called from the app whenever summary/account data changes, so every placed widget shows the latest snapshot. */
object WidgetUpdater {
    suspend fun update(context: Context, surplus: Double, accounts: List<Account>) {
        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(HouseholdWidget::class.java)
        if (ids.isEmpty()) return
        val accountsEncoded = accounts.joinToString("|") { "${it.name}:${it.balance}" }
        ids.forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    set(KEY_SURPLUS, formatInrShort(surplus))
                    set(KEY_ACCOUNTS, accountsEncoded)
                }
            }
            HouseholdWidget().update(context, id)
        }
    }
}
