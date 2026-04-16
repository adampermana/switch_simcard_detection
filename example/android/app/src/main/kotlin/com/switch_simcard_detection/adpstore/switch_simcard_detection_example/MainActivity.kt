package com.switch_simcard_detection.adpstore.switch_simcard_detection_example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createSimMonitorNotificationChannel()
    }

    /**
     * Buat notification channel sebelum flutter_background_service
     * memanggil startForeground(). Jika channel belum ada saat startForeground()
     * dipanggil, Android akan throw "Bad notification for startForeground".
     *
     * Channel ID harus SAMA PERSIS dengan yang di-configure di Dart:
     * notificationChannelId: 'sim_monitor_channel'
     */
    private fun createSimMonitorNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sim_monitor_channel",
                "SIM Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors SIM network for automatic switching"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
