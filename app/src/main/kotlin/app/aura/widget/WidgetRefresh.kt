package app.aura.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.aura.core.data.OpenMeteoWeatherRepository
import app.aura.data.CityStore
import app.aura.localNow
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Fetches the first saved city's forecast for the widget. Runs every 30 minutes; offline it
 * still redraws, so the sun and the hours strip keep moving on the saved forecast.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val city = CityStore(applicationContext).load().first()
        try {
            val forecast = OpenMeteoWeatherRepository().forecast(city, localNow(city))
            WidgetStore(applicationContext).save(forecast.toWidgetSnapshot(localNow(city)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the saved forecast; the next run tries again.
        }
        AuraWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "aura-widget-periodic"
        private const val NOW = "aura-widget-now"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build(),
            )
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}

class AuraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AuraWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
        WidgetRefreshWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancel(context)
    }
}
