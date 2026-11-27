package app.expressivecalendar.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClockReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PAUSE = "app.expressivecalendar.action.CLOCK_PAUSE"
        const val ACTION_RESUME = "app.expressivecalendar.action.CLOCK_RESUME"
        const val ACTION_STOP = "app.expressivecalendar.action.CLOCK_STOP"

        var listener: ClockActionListener? = null
    }

    interface ClockActionListener {
        fun onPause()
        fun onResume()
        fun onStop()
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_PAUSE -> listener?.onPause()
            ACTION_RESUME -> listener?.onResume()
            ACTION_STOP -> {
                listener?.onStop()
                ClockForegroundService.stop(context)
            }
        }
    }
}
