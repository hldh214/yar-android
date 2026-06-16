package dev.yar.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.yar.android.ui.YarApp

class MainActivity : ComponentActivity() {
    private var shouldShowNotificationPermissionPrompt by mutableStateOf(false)

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Media playback still works if the user denies notification permission.
        updateNotificationPermissionPrompt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateNotificationPermissionPrompt()
        setContent {
            YarApp(
                showNotificationPermissionPrompt = shouldShowNotificationPermissionPrompt,
                onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionPrompt()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!hasNotificationPermission()) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateNotificationPermissionPrompt() {
        shouldShowNotificationPermissionPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission()
    }

    private fun hasNotificationPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
