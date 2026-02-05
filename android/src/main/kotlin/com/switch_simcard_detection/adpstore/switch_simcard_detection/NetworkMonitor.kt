package com.switch_simcard_detection.adpstore.switch_simcard_detection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors

/**
 * NetworkMonitor - Monitor network quality dengan ping-based detection
 */
class NetworkMonitor(
    private val context: Context,
    private val onNetworkLost: (simSlotIndex: Int) -> Unit,
    private val onNetworkRestored: (simSlotIndex: Int) -> Unit
) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        private const val CHECK_INTERVAL_MS = 5000L
        private const val NETWORK_LOSS_THRESHOLD = 3
        private const val PING_TIMEOUT_MS = 3000
        private const val PING_HOST = "google.com"
        private const val HTTP_CHECK_URL = "https://www.google.com/generate_204"
        private const val SWITCH_COOLDOWN_MS = 10000L // Wait 10s between switches
    }
    
    private var isMonitoring = false
    private var primarySIM: Int = 0
    private var fallbackSIM: Int = 1
    
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val simSwitcher = SIMSwitcher(context)
    
    private var networkLossCount = 0
    private var lastKnownGoodSIM = -1
    private var lastSwitchTime = 0L
    
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
        
        Log.i(TAG, "Started monitoring - Primary: SIM${primarySIM + 1}, Fallback: SIM${fallbackSIM + 1}")
        
        registerNetworkCallback()
        scheduleNextCheck()
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Stopped monitoring")
    }
    
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available: $network")
                    handleNetworkAvailable()
                }
                
                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost: $network")
                    handleNetworkLost()
                }
            }
            
            connectivityManager.registerNetworkCallback(networkRequest, callback)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }
    
    private fun handleNetworkAvailable() {
        networkLossCount = 0
        
        val currentSlot = simSwitcher.getCurrentDataSIMSlot()
        if (currentSlot != lastKnownGoodSIM && lastKnownGoodSIM != -1) {
            Log.i(TAG, "Network restored on SIM${currentSlot + 1}")
            onNetworkRestored(currentSlot)
        }
        
        lastKnownGoodSIM = currentSlot
    }
    
    private fun handleNetworkLost() {
        networkLossCount++
        
        Log.w(TAG, "Network loss detected (count: $networkLossCount)")
        
        if (networkLossCount >= NETWORK_LOSS_THRESHOLD) {
            performSwitch()
        }
    }
    
    /**
     * Actually perform the SIM switch
     */
    private fun performSwitch() {
        // Check cooldown
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < SWITCH_COOLDOWN_MS) {
            Log.d(TAG, "Switch cooldown active, skipping")
            return
        }
        
        val currentSlot = simSwitcher.getCurrentDataSIMSlot()
        val targetSlot = if (currentSlot == primarySIM) fallbackSIM else primarySIM
        
        Log.i(TAG, "=== AUTO SWITCH: SIM${currentSlot + 1} -> SIM${targetSlot + 1} ===")
        
        // Check if target SIM is active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (!simSwitcher.isSIMActive(targetSlot)) {
                Log.w(TAG, "Target SIM${targetSlot + 1} is not active, cannot switch")
                return
            }
        }
        
        // Perform switch
        val success = simSwitcher.smartSwitch(targetSlot)
        
        if (success) {
            lastSwitchTime = now
            networkLossCount = 0
            Log.i(TAG, "✓ Auto-switch successful to SIM${targetSlot + 1}")
            onNetworkLost(currentSlot) // Notify callback
        } else {
            Log.e(TAG, "✗ Auto-switch failed to SIM${targetSlot + 1}")
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
     * Perform network check with ping
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
                        !pingResult && !httpResult -> {
                            Log.w(TAG, "Ping and HTTP check failed - no real internet")
                            handleNetworkLost()
                        }
                        quality == NetworkQuality.POOR && !pingResult -> {
                            Log.w(TAG, "Poor quality and ping failed")
                            networkLossCount++
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
