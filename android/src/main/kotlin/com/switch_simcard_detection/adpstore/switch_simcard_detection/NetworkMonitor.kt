package com.switch_simcard_detection.adpstore.switch_simcard_detection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission

/**
 * NetworkMonitor - Monitor network quality dan detect network loss
 * untuk automatic SIM switching
 */
class NetworkMonitor(
    private val context: Context,
    private val onNetworkLost: (simIndex: Int) -> Unit,
    private val onNetworkRestored: (simIndex: Int) -> Unit
) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        private const val CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
        private const val NETWORK_LOSS_THRESHOLD = 3 // Consider lost after 3 failed checks
    }
    
    private var isMonitoring = false
    private var primarySIM: Int = 0
    private var fallbackSIM: Int = 1
    
    private val handler = Handler(Looper.getMainLooper())
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    
    private var networkLossCount = 0
    private var lastKnownGoodSIM = -1
    
    /**
     * Start monitoring network untuk automatic switching
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    fun startMonitoring(primarySIM: Int, fallbackSIM: Int) {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring, stopping previous session")
            stopMonitoring()
        }
        
        this.primarySIM = primarySIM
        this.fallbackSIM = fallbackSIM
        this.isMonitoring = true
        this.networkLossCount = 0
        
        Log.i(TAG, "Started monitoring - Primary: SIM${primarySIM + 1}, Fallback: SIM${fallbackSIM + 1}")
        
        // Register network callback
        registerNetworkCallback()
        
        // Start periodic check
        scheduleNextCheck()
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        
        Log.i(TAG, "Stopped monitoring")
    }
    
    /**
     * Register network callback untuk real-time network changes
     */
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
                
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val downSpeed = networkCapabilities.linkDownstreamBandwidthKbps
                    val upSpeed = networkCapabilities.linkUpstreamBandwidthKbps
                    Log.d(TAG, "Network capabilities changed - Down: ${downSpeed}kbps, Up: ${upSpeed}kbps")
                }
            }
            
            connectivityManager.registerNetworkCallback(networkRequest, callback)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }
    
    /**
     * Handle network available event
     */
    private fun handleNetworkAvailable() {
        networkLossCount = 0
        
        val currentSIM = SIMSwitcher(context).getCurrentDataSIM()
        if (currentSIM != lastKnownGoodSIM && lastKnownGoodSIM != -1) {
            Log.i(TAG, "Network restored on SIM${currentSIM + 1}")
            onNetworkRestored(currentSIM)
        }
        
        lastKnownGoodSIM = currentSIM
    }
    
    /**
     * Handle network lost event
     */
    private fun handleNetworkLost() {
        networkLossCount++
        
        Log.w(TAG, "Network loss detected (count: $networkLossCount)")
        
        if (networkLossCount >= NETWORK_LOSS_THRESHOLD) {
            Log.e(TAG, "Network loss threshold reached, triggering switch")
            
            val currentSIM = SIMSwitcher(context).getCurrentDataSIM()
            val targetSIM = if (currentSIM == primarySIM) fallbackSIM else primarySIM
            
            Log.i(TAG, "Switching from SIM${currentSIM + 1} to SIM${targetSIM + 1}")
            onNetworkLost(currentSIM)
        }
    }
    
    /**
     * Schedule next periodic check
     */
    private fun scheduleNextCheck() {
        if (!isMonitoring) return
        
        handler.postDelayed({
            performNetworkCheck()
            scheduleNextCheck()
        }, CHECK_INTERVAL_MS)
    }
    
    /**
     * Perform periodic network check
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    private fun performNetworkCheck() {
        try {
            val quality = getNetworkQuality()
            
            Log.d(TAG, "Network check - Quality: $quality")
            
            when (quality) {
                NetworkQuality.NONE -> {
                    handleNetworkLost()
                }
                NetworkQuality.POOR -> {
                    Log.w(TAG, "Poor network quality detected")
                    // Could implement gradual degradation handling here
                }
                NetworkQuality.GOOD, NetworkQuality.EXCELLENT -> {
                    handleNetworkAvailable()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing network check", e)
        }
    }
    
    /**
     * Get current network quality
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    fun getNetworkQuality(): NetworkQuality {
        try {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                Log.d(TAG, "No active network")
                return NetworkQuality.NONE
            }
            
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities == null) {
                Log.d(TAG, "No network capabilities")
                return NetworkQuality.NONE
            }
            
            // Check if connected to internet
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return NetworkQuality.NONE
            }
            
            // Check if validated (actually has internet access)
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return NetworkQuality.POOR
            }
            
            // Check signal strength via TelephonyManager
            val signalStrength = telephonyManager.signalStrength
            if (signalStrength != null) {
                val level = signalStrength.level // 0-4, where 4 is best
                
                return when (level) {
                    0, 1 -> NetworkQuality.POOR
                    2 -> NetworkQuality.GOOD
                    3, 4 -> NetworkQuality.EXCELLENT
                    else -> NetworkQuality.GOOD
                }
            }
            
            // Default to GOOD if we have validated internet
            return NetworkQuality.GOOD
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network quality", e)
            return NetworkQuality.NONE
        }
    }
    
    /**
     * Check if specific SIM has network
     */
    @RequiresPermission(android.Manifest.permission.READ_PHONE_STATE)
    fun hasNetwork(simIndex: Int): Boolean {
        return try {
            val currentSIM = SIMSwitcher(context).getCurrentDataSIM()
            
            // Only accurate if checking current active SIM
            if (simIndex != currentSIM) {
                Log.w(TAG, "Cannot accurately check network for inactive SIM")
                return false
            }
            
            getNetworkQuality() != NetworkQuality.NONE
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network for SIM $simIndex", e)
            false
        }
    }
    
    /**
     * Get detailed network info
     */
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
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network info", e)
        }
        
        return info
    }
}

/**
 * Network quality levels
 */
enum class NetworkQuality {
    NONE,       // No network
    POOR,       // Connected but weak signal
    GOOD,       // Good signal
    EXCELLENT   // Excellent signal
}
