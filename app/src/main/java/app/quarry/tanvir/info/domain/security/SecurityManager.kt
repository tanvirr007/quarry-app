package app.quarry.tanvir.info.domain.security

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity

interface SecurityManager {
    fun canAuthenticate(activity: FragmentActivity): Boolean
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
}
