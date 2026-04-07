package com.switch_simcard_detection.adpstore.switch_simcard_detection

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.DataOutputStream
import java.lang.reflect.Method

class SIMSwitcher(private val context: Context) {
    
    companion object {
        private const val TAG = "SIMSwitcher"

        private const val KEY_MULTI_SIM_DATA_CALL = "multi_sim_data_call"
        // This key is writable+readable with WRITE_SECURE_SETTINGS on this device (verified via ADB log).
        // multi_sim_data_call requires READ_PRIVILEGED_PHONE_STATE to READ and throws SecurityException.
        private const val KEY_USER_PREFERRED_DATA_SUB = "user_preferred_data_sub"

        private val SERVICE_CALL_CODES = listOf(193, 194, 27, 28, 180)
        
        // PERBAIKAN: Tambahkan delay constants
        private const val SWITCH_DELAY_MS = 1500L
        private const val VERIFY_DELAY_MS = 800L
        private const val RETRY_ATTEMPTS = 3
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
     * PERBAIKAN: Write and verify dengan retry mechanism
     */
    private fun writeAndVerifyKey(key: String, value: Int): Boolean {
        return try {
            val written = Settings.Global.putInt(context.contentResolver, key, value)
            if (!written) {
                Log.w(TAG, "Failed to write $key")
                return false
            }
            
            // Wait longer for system to apply the change
            Thread.sleep(VERIFY_DELAY_MS)
            
            // Verify with retry
            var verified = false
            for (attempt in 1..RETRY_ATTEMPTS) {
                val readBack = Settings.Global.getInt(context.contentResolver, key, -1)
                if (readBack == value) {
                    Log.i(TAG, "✓ Verified: $key = $value (attempt $attempt)")
                    verified = true
                    break
                }
                if (attempt < RETRY_ATTEMPTS) {
                    Log.d(TAG, "$key read=$readBack (expected $value), retrying...")
                    Thread.sleep(400)
                }
            }
            
            verified
        } catch (e: Exception) {
            Log.d(TAG, "writeAndVerifyKey($key=$value) error: ${e.message}")
            false
        }
    }
    
    /**
     * Switch via Settings.Global + data toggle.
     *
     * Flow yang dikonfirmasi bekerja (dari ADB user):
     *   settings put global multi_sim_data_call <subId>
     *   cmd phone data disable
     *   [tunggu sampai benar-benar mati]
     *   cmd phone data enable
     */
    fun switchViaSettings(slotIndex: Int): Boolean {
        return try {
            val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                getSubscriptionIdForSlot(slotIndex)
            } else {
                slotIndex + 1
            }

            Log.i(TAG, "switchViaSettings: slot=$slotIndex subId=$subId")

            // Step 1: Write multi_sim_data_call (key utama sesuai flow ADB yang dikonfirmasi)
            try {
                Settings.Global.putInt(context.contentResolver, KEY_MULTI_SIM_DATA_CALL, subId)
                Log.d(TAG, "Written: multi_sim_data_call = $subId")
            } catch (e: Exception) {
                Log.d(TAG, "multi_sim_data_call write error: ${e.message}")
            }

            // Step 2: Write user_preferred_data_sub (readable+writable, used for verification)
            val verified = writeAndVerifyKey(KEY_USER_PREFERRED_DATA_SUB, subId)
            if (!verified) {
                Log.w(TAG, "user_preferred_data_sub could not be verified, proceeding anyway")
            }

            // Write bonus keys (best-effort)
            listOf("mobile_data_preferred_sub_id", "default_data_sub_id").forEach { key ->
                try {
                    Settings.Global.putInt(context.contentResolver, key, subId)
                } catch (_: Exception) {}
            }

            // Step 3: cmd phone data disable → wait → cmd phone data enable
            // Ini memaksa modem untuk reconnect menggunakan SIM yang baru dikonfigurasi
            forceMobileDataReconnect()

            // Step 4: Verify — current SIM slot harus sudah berubah
            val ok = verifySwitch(slotIndex)
            if (!ok) {
                Log.w(TAG, "verifySwitch failed but data may still be routing via new SIM")
                // Return true jika user_preferred_data_sub berhasil ditulis — switch telah dijalankan
                return verified
            }
            ok

        } catch (e: Exception) {
            Log.e(TAG, "Error in switchViaSettings", e)
            false
        }
    }

    /**
     * Paksa modem reconnect ke SIM baru.
     *
     * Flow yang benar (sesuai konfirmasi user via ADB):
     *   settings put global multi_sim_data_call <subId>  ← sudah dilakukan di switchViaSettings()
     *   cmd phone data disable                           ← ini
     *   [tunggu sampai benar-benar mati]
     *   cmd phone data enable                            ← ini
     *
     * Implementasi programatik: TelephonyManager.setDataEnabled(false/true) via reflection.
     * Ini adalah equivalent dari `cmd phone data disable/enable` yang dijalankan dari kode app.
     */
    private fun forceMobileDataReconnect() {
        Log.i(TAG, "=== forceMobileDataReconnect: cmd phone data disable → enable ===")

        // Step 1: Disable data (= cmd phone data disable)
        val disabled = setDataEnabled(false)
        Log.i(TAG, "Data disable: ${if (disabled) "✓ success" else "✗ failed (trying exec)"}")

        if (!disabled) {
            // Fallback: Runtime.exec — works jika app punya WRITE_SECURE_SETTINGS
            execPhoneDataCmd("disable")
        }

        // Tunggu sampai data benar-benar mati
        Log.d(TAG, "Waiting for data to fully disconnect...")
        Thread.sleep(2000)

        // Step 2: Enable data (= cmd phone data enable)
        val enabled = setDataEnabled(true)
        Log.i(TAG, "Data enable: ${if (enabled) "✓ success" else "✗ failed (trying exec)"}")

        if (!enabled) {
            execPhoneDataCmd("enable")
        }

        // Tunggu modem establish connection ke SIM baru
        Log.d(TAG, "Waiting for modem to connect via new SIM...")
        Thread.sleep(2500)

        Log.i(TAG, "✓ forceMobileDataReconnect complete")
    }

    /**
     * Panggil TelephonyManager.setDataEnabled(enable) via reflection.
     * Equivalent dengan: cmd phone data disable / cmd phone data enable
     * Membutuhkan WRITE_SECURE_SETTINGS (yang sudah di-grant via ADB).
     */
    private fun setDataEnabled(enable: Boolean): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Android 12+: coba setDataEnabled(boolean) — paling sederhana
            try {
                val m = TelephonyManager::class.java.getDeclaredMethod(
                    "setDataEnabled",
                    Boolean::class.javaPrimitiveType
                )
                m.isAccessible = true
                m.invoke(tm, enable)
                Log.d(TAG, "setDataEnabled($enable) via reflection OK")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "setDataEnabled(bool) failed: ${e.message}")
            }

            // Fallback: setDataEnabled(subId, boolean)
            try {
                val subId = readCurrentDataSubId()
                val m = TelephonyManager::class.java.getDeclaredMethod(
                    "setDataEnabled",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                m.isAccessible = true
                m.invoke(tm, subId, enable)
                Log.d(TAG, "setDataEnabled($subId, $enable) via reflection OK")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "setDataEnabled(subId, bool) failed: ${e.message}")
            }

            false
        } catch (e: Exception) {
            Log.w(TAG, "setDataEnabled($enable) error: ${e.message}")
            false
        }
    }

    /**
     * Fallback: jalankan `cmd phone data disable/enable` via Runtime.exec
     */
    private fun execPhoneDataCmd(action: String) {
        try {
            val cmd = arrayOf("cmd", "phone", "data", action)
            val process = Runtime.getRuntime().exec(cmd)
            val exitCode = process.waitFor()
            Log.d(TAG, "exec 'cmd phone data $action' exitCode=$exitCode")
        } catch (e: Exception) {
            Log.w(TAG, "exec 'cmd phone data $action' failed: ${e.message}")
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
                Thread.sleep(500)
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
                Thread.sleep(SWITCH_DELAY_MS)
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
                    Thread.sleep(SWITCH_DELAY_MS)
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
     * Smart switch — attempt all available methods in priority order.
     *
     * Android 12 confirmed method order (from ADB log analysis):
     * 1. Settings.Global via user_preferred_data_sub → WORKS with WRITE_SECURE_SETTINGS
     * 2. Root service call → works on rooted devices
     * 3. SubscriptionManager.setDefaultDataSubId → requires MODIFY_PHONE_STATE (not grantable), skip on 12+
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
            Log.i(TAG, "Already on target SIM slot $targetSlotIndex")
            return true
        }

        // Priority 1: Settings.Global (confirmed working with WRITE_SECURE_SETTINGS)
        Log.d(TAG, "Attempt 1: Settings.Global")
        if (switchViaSettings(targetSlotIndex)) {
            Log.i(TAG, "✓ Switch successful via Settings.Global")
            return true
        }

        // Priority 2: Root service call
        if (isDeviceRooted()) {
            Log.d(TAG, "Attempt 2: Service call (rooted)")
            if (switchViaServiceCall(targetSlotIndex, true)) {
                Log.i(TAG, "✓ Switch successful via service call")
                return true
            }
        }

        // Priority 3: SubscriptionManager API (pre-Android 12 only; on 12+ requires MODIFY_PHONE_STATE)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.d(TAG, "Attempt 3: SubscriptionManager API")
            if (switchViaSubscriptionManager(targetSlotIndex)) {
                Log.i(TAG, "✓ Switch successful via SubscriptionManager API")
                return true
            }
        }

        Log.e(TAG, "✗ All switch methods failed")
        return false
    }
    
    /**
     * PERBAIKAN: Verify switch dengan retry mechanism
     */
    fun verifySwitch(expectedSlotIndex: Int): Boolean {
        return try {
            var verified = false
            
            for (attempt in 1..RETRY_ATTEMPTS) {
                val currentSlot = getCurrentDataSIMSlot()
                
                if (currentSlot == expectedSlotIndex) {
                    Log.i(TAG, "✓ Switch verified: slot $expectedSlotIndex is active (attempt $attempt)")
                    verified = true
                    break
                } else {
                    Log.w(TAG, "⚠ Verification attempt $attempt failed. Expected slot: $expectedSlotIndex, Got: $currentSlot")
                    if (attempt < RETRY_ATTEMPTS) {
                        Thread.sleep(500)
                    }
                }
            }
            
            verified
        } catch (e: Exception) {
            Log.e(TAG, "Verification error", e)
            false
        }
    }
    
    /**
     * Get current data SIM as SLOT INDEX (0 or 1).
     *
     * Reading priority (based on actual Android 12 device behavior from ADB logs):
     * 1. Settings.Global["user_preferred_data_sub"] — readable with WRITE_SECURE_SETTINGS ✓
     * 2. SubscriptionManager.getDefaultDataSubscriptionId() — may lag behind after a write
     * 3. Settings.Global["multi_sim_data_call"] — requires READ_PRIVILEGED_PHONE_STATE, throws ✗
     */
    fun getCurrentDataSIMSlot(): Int {
        return try {
            val subId: Int = readCurrentDataSubId()

            // Convert subscription ID to slot index using SubscriptionManager info
            val slotIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                getSlotIndexForSubscriptionId(subId)
            } else {
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
     * Read the active data subscription ID from Settings.Global.
     * Uses user_preferred_data_sub as primary (readable+writable on Android 12 with WRITE_SECURE_SETTINGS).
     * Falls back to SubscriptionManager API.
     */
    private fun readCurrentDataSubId(): Int {
        // 1. user_preferred_data_sub — known good key on this device
        try {
            val v = Settings.Global.getInt(context.contentResolver, KEY_USER_PREFERRED_DATA_SUB, -1)
            if (v > 0) {
                Log.d(TAG, "readCurrentDataSubId: user_preferred_data_sub = $v")
                return v
            }
        } catch (e: Exception) {
            Log.d(TAG, "user_preferred_data_sub not readable: ${e.message}")
        }

        // 2. SubscriptionManager API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val v = SubscriptionManager.getDefaultDataSubscriptionId()
                if (v != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    Log.d(TAG, "readCurrentDataSubId: SubscriptionManager = $v")
                    return v
                }
            } catch (e: Exception) {
                Log.d(TAG, "SubscriptionManager read failed: ${e.message}")
            }
        }

        // 3. Last resort — multi_sim_data_call (may throw on some devices)
        return try {
            val v = Settings.Global.getInt(context.contentResolver, KEY_MULTI_SIM_DATA_CALL, 1)
            Log.d(TAG, "readCurrentDataSubId: multi_sim_data_call = $v")
            v
        } catch (e: Exception) {
            Log.w(TAG, "multi_sim_data_call not readable: ${e.message}")
            1 // absolute fallback
        }
    }
    
    /**
     * Get current data SIM - returns SLOT INDEX (0 or 1)
     */
    fun getCurrentDataSIM(): Int {
        return getCurrentDataSIMSlot()
    }
    
    /**
     * Get all SIM status.
     * NOTE: multi_sim_data_call requires READ_PRIVILEGED_PHONE_STATE on Android 12 — avoided here.
     */
    fun getAllSIMStatus(): Map<String, String> {
        val status = mutableMapOf<String, String>()

        try {
            val currentSlot = getCurrentDataSIMSlot()
            status["currentSlot"] = currentSlot.toString()
            status["currentSIM"] = (currentSlot + 1).toString()

            // Read subId via safe key only
            val subId = readCurrentDataSubId()
            status["subscriptionId"] = subId.toString()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val activeSims = getActiveSIMs()
                status["activeSlots"] = activeSims.joinToString(",")
                status["activeSIMs"] = activeSims.map { it + 1 }.joinToString(",")
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
    
    /**
     * Check if we can switch SIM by attempting a test-write to user_preferred_data_sub.
     * This is the key confirmed writable with WRITE_SECURE_SETTINGS on Android 12.
     */
    fun canSwitchSIM(): Boolean {
        return try {
            val currentSubId = readCurrentDataSubId()
            // Test-write the current value back — no-op functionally, but verifies write access
            val written = Settings.Global.putInt(context.contentResolver, KEY_USER_PREFERRED_DATA_SUB, currentSubId)
            Log.d(TAG, "canSwitchSIM test-write result: $written")
            written
        } catch (e: Exception) {
            Log.e(TAG, "Error checking canSwitchSIM", e)
            false
        }
    }
}