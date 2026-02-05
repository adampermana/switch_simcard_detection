package com.switch_simcard_detection.adpstore.switch_simcard_detection

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.DataOutputStream
import java.lang.reflect.Method

class SIMSwitcher(private val context: Context) {
    
    companion object {
        private const val TAG = "SIMSwitcher"
        
        private const val KEY_MULTI_SIM_DATA_CALL = "multi_sim_data_call"
        private val SERVICE_CALL_CODES = listOf(193, 194, 27, 28, 180)
    }
    
    /**
     * Get active subscription list
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun getActiveSubscriptions(): List<SubscriptionInfo>? {
        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subscriptionManager.activeSubscriptionInfoList
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subscriptions", e)
            null
        }
    }
    
    /**
     * Convert slot index (0, 1) to subscription ID
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun getSubscriptionIdForSlot(slotIndex: Int): Int {
        return try {
            val subscriptions = getActiveSubscriptions()
            if (subscriptions == null || subscriptions.isEmpty()) {
                Log.w(TAG, "No active subscriptions")
                return slotIndex + 1 // Fallback
            }
            
            // Find subscription matching slot index
            val sub = subscriptions.find { it.simSlotIndex == slotIndex }
            if (sub != null) {
                Log.d(TAG, "Slot $slotIndex -> SubId ${sub.subscriptionId}")
                return sub.subscriptionId
            }
            
            // Fallback: use list position
            if (slotIndex < subscriptions.size) {
                val subId = subscriptions[slotIndex].subscriptionId
                Log.d(TAG, "Slot $slotIndex -> SubId $subId (by position)")
                return subId
            }
            
            slotIndex + 1
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sub ID for slot", e)
            slotIndex + 1
        }
    }
    
    /**
     * Convert subscription ID to slot index (0, 1)
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun getSlotIndexForSubscriptionId(subId: Int): Int {
        return try {
            val subscriptions = getActiveSubscriptions()
            if (subscriptions == null || subscriptions.isEmpty()) {
                return if (subId <= 2) subId - 1 else 0
            }
            
            val sub = subscriptions.find { it.subscriptionId == subId }
            if (sub != null) {
                Log.d(TAG, "SubId $subId -> Slot ${sub.simSlotIndex}")
                return sub.simSlotIndex
            }
            
            // Fallback
            if (subId <= 2) subId - 1 else 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting slot for sub ID", e)
            if (subId <= 2) subId - 1 else 0
        }
    }
    
    /**
     * Check if SIM slot is active/enabled
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun isSIMActive(slotIndex: Int): Boolean {
        return try {
            val subscriptions = getActiveSubscriptions()
            
            if (subscriptions == null || subscriptions.isEmpty()) {
                Log.w(TAG, "No active SIM subscriptions")
                return false
            }
            
            // Check if any subscription has this slot index
            val found = subscriptions.any { it.simSlotIndex == slotIndex }
            Log.d(TAG, "SIM slot $slotIndex active: $found")
            found
            
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot check SIM status - permission denied")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SIM status", e)
            true
        }
    }
    
    /**
     * Get list of active SIM slot indexes
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun getActiveSIMs(): List<Int> {
        return try {
            val subscriptions = getActiveSubscriptions()
            
            if (subscriptions == null || subscriptions.isEmpty()) {
                return emptyList()
            }
            
            val slots = subscriptions.map { it.simSlotIndex }.sorted()
            Log.d(TAG, "Active SIM slots: $slots")
            slots
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active SIMs", e)
            emptyList()
        }
    }
    
    /**
     * Switch via ContentResolver API using subscription ID
     */
    fun switchViaSettings(slotIndex: Int): Boolean {
        return try {
            // Get proper subscription ID for the slot
            val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                getSubscriptionIdForSlot(slotIndex)
            } else {
                slotIndex + 1
            }
            
            Log.d(TAG, "Switching to slot $slotIndex (subId: $subId) via ContentResolver")
            
            // Try with subscription ID first
            val keys = listOf(
                KEY_MULTI_SIM_DATA_CALL,
                "user_preferred_data_sub",
                "mobile_data_preferred_sub_id",
                "preferred_data_subscription",
                "default_data_sub_id"
            )
            
            for (key in keys) {
                try {
                    val success = Settings.Global.putInt(context.contentResolver, key, subId)
                    
                    if (success) {
                        Log.i(TAG, "✓ Set $key = $subId")
                        Thread.sleep(500)
                        
                        val currentValue = Settings.Global.getInt(context.contentResolver, key, -1)
                        if (currentValue == subId) {
                            Log.i(TAG, "✓ Verified: $key = $subId")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Key '$key' error: ${e.message}")
                }
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error switching SIM", e)
            false
        }
    }
    
    /**
     * Toggle SIM via ContentResolver
     */
    fun toggleSIMViaSettings(slotIndex: Int, enable: Boolean): Boolean {
        return try {
            val value = if (enable) 1 else 0
            val key = "mobile_data$slotIndex"
            
            Log.d(TAG, "Toggling SIM slot $slotIndex to ${if (enable) "enabled" else "disabled"}")
            
            val success = Settings.Global.putInt(context.contentResolver, key, value)
            if (success) {
                Log.i(TAG, "✓ SIM slot $slotIndex ${if (enable) "enabled" else "disabled"}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling SIM", e)
            false
        }
    }
    
    /**
     * SubscriptionManager API (Android 5.1+)
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    fun switchViaSubscriptionManager(slotIndex: Int): Boolean {
        return try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            
            val subscriptions = subscriptionManager.activeSubscriptionInfoList
            if (subscriptions == null || subscriptions.isEmpty()) {
                Log.e(TAG, "No active SIM subscriptions")
                return false
            }
            
            // Find subscription for the slot
            val targetSub = subscriptions.find { it.simSlotIndex == slotIndex }
            if (targetSub == null) {
                Log.e(TAG, "No subscription for slot $slotIndex")
                return false
            }
            
            val targetSubId = targetSub.subscriptionId
            Log.d(TAG, "Switching to slot $slotIndex (subId: $targetSubId) via API")
            
            try {
                val setDataSubIdMethod: Method = SubscriptionManager::class.java.getDeclaredMethod(
                    "setDefaultDataSubId",
                    Int::class.javaPrimitiveType
                )
                setDataSubIdMethod.isAccessible = true
                setDataSubIdMethod.invoke(subscriptionManager, targetSubId)
                
                Log.i(TAG, "✓ API call successful")
                Thread.sleep(1000)
                return verifySwitch(slotIndex)
                
            } catch (e: Exception) {
                Log.e(TAG, "API method failed", e)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in API method", e)
            false
        }
    }
    
    /**
     * Service call (root only)
     */
    fun switchViaServiceCall(slotIndex: Int, enable: Boolean): Boolean {
        if (!isDeviceRooted()) {
            return false
        }
        
        return try {
            val enableValue = if (enable) 1 else 0
            
            for (funcCode in SERVICE_CALL_CODES) {
                val success = executeServiceCall(funcCode, slotIndex, enableValue)
                if (success) {
                    Log.i(TAG, "✓ Service call successful")
                    return verifySwitch(slotIndex)
                }
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun executeServiceCall(funcCode: Int, slot: Int, enable: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            val command = "service call phone $funcCode i32 $slot i32 $enable\n"
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
     * Smart switch with SIM availability check
     */
    fun smartSwitch(targetSlotIndex: Int): Boolean {
        Log.i(TAG, "=== Smart Switch to SIM slot $targetSlotIndex ===")
        
        if (targetSlotIndex !in 0..1) {
            Log.e(TAG, "Invalid slot index")
            return false
        }
        
        // Check if target SIM is active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (!isSIMActive(targetSlotIndex)) {
                Log.e(TAG, "✗ Target slot $targetSlotIndex is NOT ACTIVE")
                return false
            }
        }
        
        val currentSlot = getCurrentDataSIMSlot()
        if (currentSlot == targetSlotIndex) {
            Log.i(TAG, "Already on target SIM slot")
            return true
        }
        
        // Priority 1: ContentResolver API
        Log.d(TAG, "Attempt 1: ContentResolver API")
        if (switchViaSettings(targetSlotIndex)) {
            Log.i(TAG, "✓ Switch successful via ContentResolver")
            return true
        }
        
        // Priority 2: Root method
        if (isDeviceRooted()) {
            Log.d(TAG, "Attempt 2: Service call (rooted)")
            if (switchViaServiceCall(targetSlotIndex, true)) {
                Log.i(TAG, "✓ Switch successful via service call")
                return true
            }
        }
        
        // Priority 3: SubscriptionManager API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.d(TAG, "Attempt 3: SubscriptionManager API")
            if (switchViaSubscriptionManager(targetSlotIndex)) {
                Log.i(TAG, "✓ Switch successful via API")
                return true
            }
        }
        
        Log.e(TAG, "✗ All switch methods failed")
        return false
    }
    
    /**
     * Verify switch
     */
    fun verifySwitch(expectedSlotIndex: Int): Boolean {
        return try {
            val currentSlot = getCurrentDataSIMSlot()
            
            if (currentSlot == expectedSlotIndex) {
                Log.i(TAG, "✓ Switch verified: slot $expectedSlotIndex is active")
                true
            } else {
                Log.w(TAG, "⚠ Verification failed. Expected slot: $expectedSlotIndex, Got: $currentSlot")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verification error", e)
            false
        }
    }
    
    /**
     * Get current data SIM as SLOT INDEX (0 or 1)
     * This is the main method for consistent slot-based reporting
     */
    fun getCurrentDataSIMSlot(): Int {
        return try {
            val subId = Settings.Global.getInt(context.contentResolver, KEY_MULTI_SIM_DATA_CALL, 1)
            
            // Convert subscription ID to slot index
            val slotIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                getSlotIndexForSubscriptionId(subId)
            } else {
                // Legacy: assume 1-based
                if (subId <= 2) subId - 1 else 0
            }
            
            Log.d(TAG, "Current data SIM: slot $slotIndex (subId: $subId)")
            slotIndex
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current SIM", e)
            0
        }
    }
    
    /**
     * Get current data SIM - returns SLOT INDEX (0 or 1)
     */
    fun getCurrentDataSIM(): Int {
        return getCurrentDataSIMSlot()
    }
    
    /**
     * Get all SIM status
     */
    fun getAllSIMStatus(): Map<String, String> {
        val status = mutableMapOf<String, String>()
        
        try {
            // Get current slot
            val currentSlot = getCurrentDataSIMSlot()
            status["currentSlot"] = currentSlot.toString()
            status["currentSIM"] = (currentSlot + 1).toString()
            
            // Get subscription ID
            val subId = Settings.Global.getInt(context.contentResolver, KEY_MULTI_SIM_DATA_CALL, -1)
            status["subscriptionId"] = subId.toString()
            
            // Active SIMs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val activeSims = getActiveSIMs()
                status["activeSlots"] = activeSims.joinToString(",")
                status["activeSIMs"] = activeSims.map { it + 1 }.joinToString(",")
            }
            
            // Legacy keys
            val legacyKeys = listOf("mobile_data", "mobile_data0", "mobile_data1")
            for (key in legacyKeys) {
                try {
                    val value = Settings.Global.getInt(context.contentResolver, key, -999)
                    status[key] = if (value == -999) "null" else value.toString()
                } catch (e: Exception) {
                    status[key] = "null"
                }
            }
            
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
            val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                getSubscriptionIdForSlot(0)
            } else {
                1
            }
            val testSuccess = Settings.Global.putInt(context.contentResolver, KEY_MULTI_SIM_DATA_CALL, subId)
            testSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }
}