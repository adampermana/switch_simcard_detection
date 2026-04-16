package com.switch_simcard_detection.adpstore.switch_simcard_detection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
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
        private const val CHECK_INTERVAL_MS = 5000L
        // Reduced: 2 consecutive failures = ~10s before triggering switch (was 3 = ~15s)
        private const val NETWORK_LOSS_THRESHOLD = 2
        private const val PING_TIMEOUT_MS = 3000
        private const val PING_HOST = "google.com"
        private const val HTTP_CHECK_URL = "https://www.google.com/generate_204"
        // Cooldown between switches: 8s (was 10s). Prevents thrash while still allowing recovery.
        private const val SWITCH_COOLDOWN_MS = 8000L
        // If both SIMs recently failed, pause before probing again to avoid switch ping-pong.
        private const val BOTH_SIMS_RETRY_DELAY_MS = 20000L
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
        
        registerNetworkCallback()
        scheduleNextCheck()
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        unregisterNetworkCallback()
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
    
    private fun handleNetworkAvailable() {
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

        // Notify: the plugin will call smartSwitch() once
        onNetworkLost(lostSlot)
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
