package com.switch_simcard_detection.adpstore.switch_simcard_detection

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.lang.reflect.Method

class SIMSwitcher(private val context: Context) {
    
    companion object {
        private const val TAG = "SIMSwitcher"
        
        private val SETTING_KEYS = listOf(
            "multi_sim_data_call",
            "user_preferred_data_sub",
            "mobile_data_preferred_sub_id",
            "preferred_data_subscription",
            "default_data_sub_id"
        )
        
        private const val KEY_MULTI_SIM_DATA_CALL = "multi_sim_data_call"
        private val SERVICE_CALL_CODES = listOf(193, 194, 27, 28, 180)
    }
    
    /**
     * Method 1: Switch via ContentResolver API (NOT shell command!)
     * This uses Settings.Global.putInt() directly
     */
    fun switchViaSettings(simIndex: Int): Boolean {
        return try {
            val simValue = simIndex + 1
            Log.d(TAG, "Attempting to switch to SIM $simValue via ContentResolver API")
            
            for (key in SETTING_KEYS) {
                try {
                    // Use ContentResolver directly - NOT shell command!
                    val success = Settings.Global.putInt(context.contentResolver, key, simValue)
                    
                    if (success) {
                        Log.i(TAG, "✓ Successfully set $key = $simValue via ContentResolver")
                        Thread.sleep(500)
                        
                        // Verify
                        val currentValue = Settings.Global.getInt(context.contentResolver, key, -1)
                        if (currentValue == simValue) {
                            Log.i(TAG, "✓ Verified: $key is now $simValue")
                            return true
                        }
                    }
                } catch (e: SecurityException) {
                    Log.d(TAG, "Key '$key' failed: ${e.message}")
                } catch (e: Exception) {
                    Log.d(TAG, "Key '$key' error: ${e.message}")
                }
            }
            
            Log.e(TAG, "✗ Failed to switch SIM via all setting keys")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error switching SIM via settings", e)
            false
        }
    }
    
    /**
     * Method 2: Toggle SIM via ContentResolver
     */
    fun toggleSIMViaSettings(simIndex: Int, enable: Boolean): Boolean {
        return try {
            val value = if (enable) 1 else 0
            val key = "mobile_data$simIndex"
            
            Log.d(TAG, "Toggling SIM $simIndex to ${if (enable) "enabled" else "disabled"}")
            
            val success = Settings.Global.putInt(context.contentResolver, key, value)
            
            if (success) {
                Log.i(TAG, "✓ SIM $simIndex ${if (enable) "enabled" else "disabled"}")
                true
            } else {
                Log.e(TAG, "✗ Failed to toggle SIM $simIndex")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling SIM", e)
            false
        }
    }
    
    /**
     * Method 3: SubscriptionManager API (Android 5.1+)
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun switchViaSubscriptionManager(simIndex: Int): Boolean {
        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            if (activeSubscriptions == null || activeSubscriptions.isEmpty()) {
                Log.e(TAG, "No active SIM subscriptions found")
                return false
            }
            
            if (simIndex >= activeSubscriptions.size) {
                Log.e(TAG, "SIM index $simIndex out of range")
                return false
            }
            
            val targetSubId = activeSubscriptions[simIndex].subscriptionId
            Log.d(TAG, "Attempting to switch to SIM ${simIndex + 1} (subId: $targetSubId) via API")
            
            try {
                val setDataSubIdMethod: Method = SubscriptionManager::class.java.getDeclaredMethod(
                    "setDefaultDataSubId",
                    Int::class.javaPrimitiveType
                )
                setDataSubIdMethod.isAccessible = true
                setDataSubIdMethod.invoke(subscriptionManager, targetSubId)
                
                Log.i(TAG, "✓ API call successful")
                Thread.sleep(1000)
                return verifySwitch(simIndex)
                
            } catch (reflectionError: Exception) {
                Log.e(TAG, "API method failed", reflectionError)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in API method", e)
            false
        }
    }
    
    /**
     * Method 4: Service call (root only)
     */
    fun switchViaServiceCall(simSlot: Int, enable: Boolean): Boolean {
        if (!isDeviceRooted()) {
            return false
        }
        
        return try {
            val enableValue = if (enable) 1 else 0
            
            for (funcCode in SERVICE_CALL_CODES) {
                val success = executeServiceCall(funcCode, simSlot, enableValue)
                if (success) {
                    Log.i(TAG, "✓ Service call successful with code $funcCode")
                    return verifySwitch(simSlot)
                }
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun executeServiceCall(funcCode: Int, simSlot: Int, enable: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            val command = "service call phone $funcCode i32 $simSlot i32 $enable\n"
            os.writeBytes(command)
            os.flush()
            os.writeBytes("exit\n")
            os.flush()
            
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Smart switch with priority order
     */
    fun smartSwitch(targetSIMIndex: Int): Boolean {
        Log.i(TAG, "=== Smart Switch to SIM ${targetSIMIndex + 1} ===")
        
        if (targetSIMIndex !in 0..1) {
            Log.e(TAG, "Invalid SIM index")
            return false
        }
        
        val currentSIM = getCurrentDataSIM()
        if (currentSIM == targetSIMIndex) {
            Log.i(TAG, "Already on target SIM")
            return true
        }
        
        // Priority 1: ContentResolver API (should work with WRITE_SECURE_SETTINGS)
        Log.d(TAG, "Attempt 1: ContentResolver API")
        if (switchViaSettings(targetSIMIndex)) {
            Log.i(TAG, "✓ Switch successful via ContentResolver")
            return true
        }
        
        // Priority 2: Root method if available
        if (isDeviceRooted()) {
            Log.d(TAG, "Attempt 2: Service call (rooted)")
            if (switchViaServiceCall(targetSIMIndex, true)) {
                Log.i(TAG, "✓ Switch successful via service call")
                return true
            }
        }
        
        // Priority 3: SubscriptionManager API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.d(TAG, "Attempt 3: SubscriptionManager API")
            if (switchViaSubscriptionManager(targetSIMIndex)) {
                Log.i(TAG, "✓ Switch successful via API")
                return true
            }
        }
        
        Log.e(TAG, "✗ All switch methods failed")
        return false
    }
    
    /**
     * Verify switch using ContentResolver
     */
    fun verifySwitch(expectedSIMIndex: Int): Boolean {
        return try {
            val currentSIM = getCurrentDataSIM()
            
            if (currentSIM == expectedSIMIndex) {
                Log.i(TAG, "✓ Switch verified: SIM ${expectedSIMIndex + 1} is active")
                true
            } else {
                Log.w(TAG, "⚠ Verification failed. Expected: ${expectedSIMIndex + 1}, Got: ${currentSIM + 1}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verification error", e)
            false
        }
    }
    
    /**
     * Get current data SIM using ContentResolver
     */
    fun getCurrentDataSIM(): Int {
        return try {
            val simValue = Settings.Global.getInt(context.contentResolver, KEY_MULTI_SIM_DATA_CALL, 1)
            val simIndex = simValue - 1
            
            Log.d(TAG, "Current data SIM: ${simIndex + 1} (index: $simIndex)")
            simIndex
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current SIM", e)
            -1
        }
    }
    
    /**
     * Get all SIM status using ContentResolver
     */
    fun getAllSIMStatus(): Map<String, String> {
        val status = mutableMapOf<String, String>()
        
        try {
            val keys = listOf(
                "mobile_data0",
                "mobile_data1",
                "mobile_data2",
                "mobile_data",
                KEY_MULTI_SIM_DATA_CALL,
                "multi_sim_defaut_data_call"
            )
            
            for (key in keys) {
                try {
                    val value = Settings.Global.getInt(context.contentResolver, key, -999)
                    status[key] = if (value == -999) "null" else value.toString()
                } catch (e: Exception) {
                    status[key] = "null"
                }
            }
            
            Log.d(TAG, "SIM Status: $status")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SIM status", e)
        }
        
        return status
    }
    
    fun isDeviceRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    fun canSwitchSIM(): Boolean {
        return try {
            // Test write permission using ContentResolver
            val currentValue = Settings.Global.getInt(context.contentResolver, KEY_MULTI_SIM_DATA_CALL, 1)
            val testSuccess = Settings.Global.putInt(context.contentResolver, KEY_MULTI_SIM_DATA_CALL, currentValue)
            testSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }
}