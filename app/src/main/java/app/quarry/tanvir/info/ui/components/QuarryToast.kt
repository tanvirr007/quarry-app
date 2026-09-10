package app.quarry.tanvir.info.ui.components

import android.content.Context
import android.widget.Toast

/**
 * Centralized toast utility that cancels the previous toast before showing a new one.
 * Prevents toast queue buildup from rapid repeated actions (e.g., copy button spam).
 */
object QuarryToast {

    private var activeToast: Toast? = null

    /**
     * Shows a toast message, cancelling any currently visible toast first.
     * This ensures only one toast is ever displayed at a time, preventing
     * the queue of stale toasts from lingering across screen navigation.
     */
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        activeToast?.cancel()
        activeToast = Toast.makeText(context.applicationContext, message, duration).also {
            it.show()
        }
    }
}
