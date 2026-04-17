package com.switch_simcard_detection.adpstore.switch_simcard_detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors

/**
 * NetworkMonitor - Monitor network quality and usable internet for the active data SIM
 */
class NetworkMonitor(
    private val context: Context,
    private val onNetworkLost: (simSlotIndex: Int) -> Unit,
    private val onNetworkRestored: (simSlotIndex: Int) -> Unit
) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        // Polling setiap 3 detik — lebih responsif dari 5 detik
        private const val CHECK_INTERVAL_MS = 3000L
        // 2 kegagalan berurutan (~6 detik) sebelum trigger switch.
        // Aman karena subscriptionChangedReceiver reset counter saat user manual switch SIM.
        private const val NETWORK_LOSS_THRESHOLD = 2
        // Timeout 2 detik untuk ping & HTTP (lebih cepat dari 3 detik)
        private const val PING_TIMEOUT_MS = 2000
        private const val PING_HOST = "google.com"
        private const val HTTP_CHECK_URL = "https://www.google.com/generate_204"
        // Cooldown 5 detik antar switch (cukup untuk mencegah ping-pong)
        private const val SWITCH_COOLDOWN_MS = 5000L
        // Jika kedua SIM baru saja gagal, tunggu 10 detik sebelum coba lagi
        private const val BOTH_SIMS_RETRY_DELAY_MS = 10000L
    }

    private var isMonitoring = false
    private var primarySIM: Int = 0
    private var fallbackSIM: Int = 1

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    // simSwitcher is kept for READ-ONLY queries (getCurrentDataSIMSlot, isSIMActive, etc.)
    // It is NOT used for switching — performSwitch() only calls onNetworkLost() so the
    // plugin handles the switch. This prevents the previous double-switch bug.
    private val simSwitcher = SIMSwitcher(context)

    private var networkLossCount = 0
    private var lastKnownGoodSIM = -1
    private var lastSwitchTime = 0L
    private var dualSimRetryBlockedUntil = 0L
    private val recentFailureTimes = mutableMapOf<Int, Long>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var airplaneModeReceiver: BroadcastReceiver? = null
    private var subscriptionChangedReceiver: BroadcastReceiver? = null
    
    /**
     * Start monitoring network
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    fun startMonitoring(primarySIM: Int, fallbackSIM: Int) {
        if (isMonitoring) {
            stopMonitoring()
        }
        
        this.primarySIM = primarySIM
        this.fallbackSIM = fallbackSIM
        this.isMonitoring = true
        this.networkLossCount = 0
        this.lastSwitchTime = 0L
        this.dualSimRetryBlockedUntil = 0L
        this.recentFailureTimes.clear()
        this.lastKnownGoodSIM = simSwitcher.getCurrentDataSIMSlot()
        
        Log.i(TAG, "Started monitoring - Primary: SIM${primarySIM + 1}, Fallback: SIM${fallbackSIM + 1}")
        
        registerSubscriptionChangedReceiver()
        registerAirplaneModeReceiver()
        registerNetworkCallback()
        scheduleNextCheck()
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        unregisterNetworkCallback()
        unregisterAirplaneModeReceiver()
        unregisterSubscriptionChangedReceiver()
        Log.i(TAG, "Stopped monitoring")
    }
    
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    private fun registerNetworkCallback() {
        try {
            unregisterNetworkCallback()

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available: $network")
                    handleNetworkAvailable()
                }
                
                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost: $network")
                    handleNetworkLost()
                }
            }
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return

        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            Log.d(TAG, "Network callback already unregistered: ${e.message}")
        } finally {
            networkCallback = null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Airplane mode receiver
    // Ketika airplane mode di-OFF-kan, Android mungkin tidak secara otomatis
    // me-restore NetworkCallback yang lama. Receiver ini memastikan callback
    // di-re-register sehingga deteksi koneksi kembali berjalan normal.
    // ─────────────────────────────────────────────────────────────────────────

    private fun registerAirplaneModeReceiver() {
        if (airplaneModeReceiver != null) return

        airplaneModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_AIRPLANE_MODE_CHANGED) return
                val isAirplaneOn = intent.getBooleanExtra("state", false)

                if (isAirplaneOn) {
                    // ✈️ Airplane mode ON
                    Log.i(TAG, "✈️ Airplane mode ON — pausing switch logic, unregistering callback")
                    networkLossCount = 0
                    dualSimRetryBlockedUntil = 0L
                    recentFailureTimes.clear()
                    // Unregister callback to avoid spurious onLost events
                    unregisterNetworkCallback()
                } else {
                    // ✈️ Airplane mode OFF — re-register callback and check immediately
                    Log.i(TAG, "✈️ Airplane mode OFF — resuming monitoring")
                    networkLossCount = 0
                    dualSimRetryBlockedUntil = 0L
                    recentFailureTimes.clear()
                    lastKnownGoodSIM = -1 // akan di-update saat onAvailable()
                    registerNetworkCallback()
                    // Delay 4s: cukup untuk data seluler aktif kembali
                    // setelah airplane mode dimatikan.
                    handler.postDelayed({
                        if (isMonitoring) performNetworkCheck()
                    }, 4000L)
                }
            }
        }

        try {
            val filter = IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            context.registerReceiver(airplaneModeReceiver, filter)
            Log.d(TAG, "Airplane mode receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register airplane mode receiver", e)
            airplaneModeReceiver = null
        }
    }

    private fun unregisterAirplaneModeReceiver() {
        try {
            airplaneModeReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.d(TAG, "Airplane mode receiver already unregistered")
        } finally {
            airplaneModeReceiver = null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Subscription changed receiver
    // Mendeteksi ketika user manual ganti default data SIM via Android Settings.
    // Tanpa ini, monitor akan deteksi SIM lama "lost" dan coba BALIK ke SIM lama →
    // konflik/exception → background service crash → notifikasi hilang.
    // ─────────────────────────────────────────────────────────────────────────

    private fun registerSubscriptionChangedReceiver() {
        if (subscriptionChangedReceiver != null) return

        subscriptionChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != "android.telephony.action.DEFAULT_DATA_SUBSCRIPTION_CHANGED") return

                val newDataSIM = simSwitcher.getCurrentDataSIMSlot()
                Log.i(TAG, "📱 System changed default data SIM to SIM${newDataSIM + 1}")

                // Reset semua counter agar tidak terjadi false switch
                networkLossCount = 0
                dualSimRetryBlockedUntil = 0L
                recentFailureTimes.clear()

                // Terapkan cooldown agar monitor tidak langsung switch lagi
                lastSwitchTime = System.currentTimeMillis()
                lastKnownGoodSIM = newDataSIM

                // Update primary/fallback mengikuti pilihan user:
                // SIM yang dipilih user menjadi primary, SIM lainnya menjadi fallback
                if (newDataSIM in 0..1) {
                    primarySIM = newDataSIM
                    fallbackSIM = if (newDataSIM == 0) 1 else 0
                    Log.i(TAG, "  Updated monitoring: primary=SIM${primarySIM + 1}, fallback=SIM${fallbackSIM + 1}")
                }

                // Re-register callback karena perubahan subscription bisa invalidate yang lama
                registerNetworkCallback()
            }
        }

        try {
            val filter = IntentFilter("android.telephony.action.DEFAULT_DATA_SUBSCRIPTION_CHANGED")
            context.registerReceiver(subscriptionChangedReceiver, filter)
            Log.d(TAG, "Subscription changed receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register subscription changed receiver", e)
            subscriptionChangedReceiver = null
        }
    }

    private fun unregisterSubscriptionChangedReceiver() {
        try {
            subscriptionChangedReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.d(TAG, "Subscription changed receiver already unregistered")
        } finally {
            subscriptionChangedReceiver = null
        }
    }


    private fun handleNetworkAvailable() {
        if (isAirplaneModeOn()) {
            Log.d(TAG, "Ignoring network available callback while airplane mode is ON")
            networkLossCount = 0
            return
        }

        networkLossCount = 0
        
        val currentSlot = simSwitcher.getCurrentDataSIMSlot()
        recentFailureTimes.remove(currentSlot)
        dualSimRetryBlockedUntil = 0L

        if (currentSlot != lastKnownGoodSIM && lastKnownGoodSIM != -1) {
            Log.i(TAG, "Network restored on SIM${currentSlot + 1}")
            onNetworkRestored(currentSlot)
        }
        
        lastKnownGoodSIM = currentSlot
    }
    
    private fun handleNetworkLost() {
        if (isAirplaneModeOn()) {
            Log.i(TAG, "Airplane mode is ON, skipping network-loss handling")
            networkLossCount = 0
            return
        }

        networkLossCount++
        
        val currentSlot = simSwitcher.getCurrentDataSIMSlot()
        Log.w(TAG, "Network loss detected on SIM${currentSlot + 1} (count: $networkLossCount)")
        
        if (networkLossCount >= NETWORK_LOSS_THRESHOLD) {
            performSwitch()
        }
    }
    
    /**
     * Notify the plugin that the network has been lost and a switch is needed.
     *
     * IMPORTANT: This method no longer performs the SIM switch itself. It only:
     * 1. Enforces a cooldown so we don't spam the callback
     * 2. Calls onNetworkLost() so the plugin (SwitchSimcardDetectionPlugin) can switch
     *
     * This eliminates the double-switch bug where both NetworkMonitor AND the plugin
     * callback were independently calling smartSwitch(), causing the SIM to switch
     * and then immediately revert.
     */
    private fun performSwitch() {
        if (isAirplaneModeOn()) {
            Log.i(TAG, "Airplane mode is ON, skipping SIM auto-switch")
            networkLossCount = 0
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < SWITCH_COOLDOWN_MS) {
            Log.d(TAG, "Switch cooldown active, skipping")
            return
        }

        if (now < dualSimRetryBlockedUntil) {
            Log.w(TAG, "Both SIMs recently failed, waiting before retrying switch")
            return
        }

        val lostSlot = simSwitcher.getCurrentDataSIMSlot()
        val targetSlot = getOtherManagedSIM(lostSlot)

        if (targetSlot == null) {
            Log.w(TAG, "Could not determine target SIM for slot $lostSlot")
            return
        }

        recentFailureTimes[lostSlot] = now

        val targetFailedAt = recentFailureTimes[targetSlot]
        if (targetFailedAt != null && now - targetFailedAt < BOTH_SIMS_RETRY_DELAY_MS) {
            networkLossCount = 0
            dualSimRetryBlockedUntil = now + BOTH_SIMS_RETRY_DELAY_MS
            Log.w(
                TAG,
                "SIM${lostSlot + 1} and SIM${targetSlot + 1} both look offline. Holding switch attempts for ${BOTH_SIMS_RETRY_DELAY_MS / 1000}s"
            )
            return
        }

        Log.i(TAG, "=== NO INTERNET on SIM${lostSlot + 1} — notifying plugin to switch to SIM${targetSlot + 1} ===")
        lastSwitchTime = now
        networkLossCount = 0

        // Notify: the plugin will call smartSwitch() once.
        // Wrapped in try-catch to prevent uncaught exception on main thread
        // that would kill the background service.
        try {
            onNetworkLost(lostSlot)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in onNetworkLost callback — background service protected", e)
        }
    }
    
    private fun scheduleNextCheck() {
        if (!isMonitoring) return
        
        handler.postDelayed({
            performNetworkCheck()
            scheduleNextCheck()
        }, CHECK_INTERVAL_MS)
    }
    
    /**
     * Perform network check.
     *
     * HTTP reachability is treated as the source of truth for usable internet.
     * If the fetch fails or returns a non-200/204 response, the active SIM is
     * considered offline and becomes eligible for auto-switching.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    private fun performNetworkCheck() {
        executor.execute {
            try {
                if (isAirplaneModeOn()) {
                    Log.d(TAG, "Skipping network check while airplane mode is ON")
                    handler.post {
                        networkLossCount = 0
                    }
                    return@execute
                }

                val quality = getNetworkQuality()
                val pingResult = pingGoogle()
                val httpResult = checkHttpConnectivity()
                
                Log.d(TAG, "Network check - Quality: $quality, Ping: $pingResult, HTTP: $httpResult")
                
                handler.post {
                    when {
                        quality == NetworkQuality.NONE -> {
                            handleNetworkLost()
                        }
                        !httpResult -> {
                            Log.w(TAG, "HTTP check failed - active SIM has no usable internet")
                            handleNetworkLost()
                        }
                        quality == NetworkQuality.POOR && !pingResult -> {
                            Log.w(TAG, "Signal is poor, but HTTP still works - keeping current SIM")
                            handleNetworkAvailable()
                        }
                        else -> {
                            handleNetworkAvailable()
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error performing network check", e)
            }
        }
    }

    private fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read airplane mode state", e)
            false
        }
    }
    
    /**
     * Ping google.com
     */
    fun pingGoogle(): Boolean {
        return try {
            val address = InetAddress.getByName(PING_HOST)
            val reachable = address.isReachable(PING_TIMEOUT_MS)
            Log.d(TAG, "Ping to $PING_HOST: ${if (reachable) "SUCCESS" else "FAILED"}")
            reachable
        } catch (e: Exception) {
            Log.d(TAG, "Ping failed: ${e.message}")
            false
        }
    }
    
    /**
     * HTTP check
     */
    fun checkHttpConnectivity(): Boolean {
        return try {
            val url = URL(HTTP_CHECK_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = PING_TIMEOUT_MS
            connection.readTimeout = PING_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            val success = responseCode == 204 || responseCode == 200
            Log.d(TAG, "HTTP check: ${if (success) "SUCCESS" else "FAILED"} (code: $responseCode)")
            success
        } catch (e: Exception) {
            Log.d(TAG, "HTTP check failed: ${e.message}")
            false
        }
    }

    private fun getOtherManagedSIM(currentSlot: Int): Int? {
        return when (currentSlot) {
            primarySIM -> fallbackSIM
            fallbackSIM -> primarySIM
            0 -> 1
            1 -> 0
            else -> null
        }
    }
    
    /**
     * Check if SIM slot is active
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun isSIMActive(slotIndex: Int): Boolean {
        return simSwitcher.isSIMActive(slotIndex)
    }
    
    /**
     * Get active SIM slots
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun getActiveSIMs(): List<Int> {
        return simSwitcher.getActiveSIMs()
    }
    
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    fun getNetworkQuality(): NetworkQuality {
        try {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                return NetworkQuality.NONE
            }
            
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities == null) {
                return NetworkQuality.NONE
            }
            
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return NetworkQuality.NONE
            }
            
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return NetworkQuality.POOR
            }
            
            val signalStrength = telephonyManager.signalStrength
            if (signalStrength != null) {
                val level = signalStrength.level
                
                return when (level) {
                    0 -> NetworkQuality.NONE
                    1 -> NetworkQuality.POOR
                    2 -> NetworkQuality.GOOD
                    3, 4 -> NetworkQuality.EXCELLENT
                    else -> NetworkQuality.GOOD
                }
            }
            
            return NetworkQuality.GOOD
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network quality", e)
            return NetworkQuality.NONE
        }
    }
    
    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    fun hasNetwork(slotIndex: Int): Boolean {
        return try {
            val currentSlot = simSwitcher.getCurrentDataSIMSlot()
            
            if (slotIndex != currentSlot) {
                return false
            }
            
            getNetworkQuality() != NetworkQuality.NONE
            
        } catch (e: Exception) {
            false
        }
    }
    
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    fun getNetworkInfo(): Map<String, Any> {
        val info = mutableMapOf<String, Any>()
        
        try {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            
            info["hasNetwork"] = activeNetwork != null
            info["hasInternet"] = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
            info["isValidated"] = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
            info["quality"] = getNetworkQuality().name
            
            capabilities?.let {
                info["downSpeed"] = it.linkDownstreamBandwidthKbps
                info["upSpeed"] = it.linkUpstreamBandwidthKbps
            }
            
            val signalStrength = telephonyManager.signalStrength
            info["signalLevel"] = signalStrength?.level ?: -1
            
            // Current SIM slot
            val currentSlot = simSwitcher.getCurrentDataSIMSlot()
            info["currentSlot"] = currentSlot
            info["currentSIM"] = currentSlot + 1
            
            // Active SIMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                info["activeSlots"] = getActiveSIMs().joinToString(",")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network info", e)
        }
        
        return info
    }
}

enum class NetworkQuality {
    NONE,
    POOR,
    GOOD,
    EXCELLENT
}
