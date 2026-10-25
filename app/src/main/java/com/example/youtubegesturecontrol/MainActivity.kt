package com.example.youtubegesturecontrol

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.youtubegesturecontrol.databinding.ActivityMainBinding
import com.example.youtubegesturecontrol.precondition.PrerequisiteChecker
import com.example.youtubegesturecontrol.precondition.PrerequisiteReport
import com.example.youtubegesturecontrol.service.ControlService
import com.example.youtubegesturecontrol.service.ControlSnapshot
import com.example.youtubegesturecontrol.service.ControlState
import com.example.youtubegesturecontrol.service.isServiceActive
import kotlinx.coroutines.launch

/**
 * 권한 안내와 시작/중지 버튼만 담당합니다. 카메라와 호출어는 서비스가 소유합니다.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val checker by lazy { PrerequisiteChecker(applicationContext) }
    private val permissionMemory by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var snapshot = ControlSnapshot()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // 결과를 받은 뒤 자동으로 다시 묻지 않습니다. 사용자가 버튼을 다시 눌러야 합니다.
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.requestPermissionsButton.setOnClickListener { requestPermissionsForControl() }
        binding.appSettingsButton.setOnClickListener { openAppSettings() }
        binding.notificationAccessButton.setOnClickListener { explainNotificationAccess() }
        binding.overlaySettingsButton.setOnClickListener { explainOverlay() }
        binding.startControlButton.setOnClickListener { onStartClicked() }
        binding.stopControlButton.setOnClickListener { ControlService.stop(this) }
        collectServiceState()
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 돌아온 뒤 알림 접근과 권한을 다시 읽습니다.
        render()
    }

    private fun collectServiceState() {
        val repository = (application as YouTubeGestureApplication).statusRepository
        lifecycleScope.launch {
            // STARTED 아래에서 수집을 멈추면, 화면이 사라진 뒤 파괴된 뷰를 갱신하지 않습니다.
            // StateFlow는 다시 구독할 때 마지막 값을 바로 줍니다.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.snapshot.collect { latest ->
                    snapshot = latest
                    render()
                }
            }
        }
    }

    private fun onStartClicked() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            showMessage(getString(R.string.start_requires_visible_activity))
            return
        }
        val missing = checker.missingRuntimePermissions()
        if (missing.isNotEmpty()) {
            requestOrOpenSettings(missing)
            return
        }
        val report = currentReport()
        if (!report.listenerEnabled) {
            explainNotificationAccess()
            return
        }
        if (!report.wakeWord.configured) {
            showMessage(report.wakeWord.detail)
            return
        }
        if (!report.modelInstalled) {
            showMessage(getString(R.string.model_missing))
            return
        }
        if (!Settings.canDrawOverlays(this) && !permissionMemory.getBoolean(OVERLAY_LATER, false)) {
            explainOverlay(startAfterPostpone = true)
            return
        }
        ControlService.startWakeWordControl(this)
    }

    private fun requestPermissionsForControl() {
        val missing = checker.missingRuntimePermissions()
        if (missing.isEmpty()) {
            showMessage(getString(R.string.permissions_already_granted))
            return
        }
        requestOrOpenSettings(missing)
    }

    private fun requestOrOpenSettings(missing: Array<String>) {
        val report = currentReport()
        val permanent = missing.any { permission -> report.permanentlyDenied.contains(permission) }
        if (permanent) {
            showMessage(getString(R.string.permission_permanently_denied))
            openAppSettings()
            return
        }
        missing.forEach { permission ->
            permissionMemory.edit().putBoolean(askedKey(permission), true).apply()
        }
        permissionLauncher.launch(missing)
    }

    private fun explainOverlay(startAfterPostpone: Boolean = false) {
        AlertDialog.Builder(this)
            .setTitle(R.string.overlay_title)
            .setMessage(R.string.overlay_message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openOverlaySettings() }
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (startAfterPostpone) {
                    permissionMemory.edit().putBoolean(OVERLAY_LATER, true).apply()
                    ControlService.startWakeWordControl(this)
                }
            }
            .show()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(intent)
        } catch (error: Exception) {
            showMessage(getString(R.string.settings_open_failed))
        }
    }

    private fun explainNotificationAccess() {
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_access_title)
            .setMessage(R.string.notification_access_message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openNotificationListenerSettings() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (error: Exception) {
            showMessage(getString(R.string.settings_open_failed))
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(intent)
        } catch (error: Exception) {
            showMessage(getString(R.string.settings_open_failed))
        }
    }

    private fun render() {
        val report = currentReport()
        binding.permissionStatus.text = checker.describe(report)
        binding.wakeWordStatus.text = getString(
            R.string.wake_word_status,
            report.wakeWord.phraseLabel,
        )
        val state = snapshot.state
        binding.serviceState.text = if (state is ControlState.Error) {
            UserMessages.stateLabel(state) + "\n" + state.message
        } else {
            UserMessages.stateLabel(state)
        }
        binding.recentResult.text = snapshot.recentResult
        binding.startControlButton.isEnabled = !state.isServiceActive() && report.canStartWakeWordControl()
        binding.stopControlButton.isEnabled = state.isServiceActive()
        binding.notificationWarning.isVisible = report.notificationPermissionRequired && !report.notificationsGranted
        binding.overlaySettingsButton.isVisible = !Settings.canDrawOverlays(this)
    }

    private fun currentReport(): PrerequisiteReport {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val asked = permissions
            .filter { permission -> permissionMemory.getBoolean(askedKey(permission), false) }
            .toSet()
        return checker.report(this, asked)
    }

    private fun askedKey(permission: String): String = "asked_$permission"

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val PREFS = "permission_requests"
        private const val OVERLAY_LATER = "overlay_later"
    }
}
