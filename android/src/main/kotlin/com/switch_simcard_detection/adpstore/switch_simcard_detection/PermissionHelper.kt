package com.switch_simcard_detection.adpstore.switch_simcard_detection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log  // ← TAMBAHKAN INI!
import androidx.core.content.ContextCompat

/**
 * PermissionHelper - Helper untuk check permissions dan provide instructions
 */
object PermissionHelper {
    
    private const val TAG = "PermissionHelper"
    
    /**
     * Check apakah semua required permissions sudah granted
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        val permissions = getRequiredPermissions()
        
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get list of required permissions
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        // Android 12+ requires READ_PHONE_NUMBERS for subscription info
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        
        return permissions
    }
    
    /**
     * Get list of missing permissions
     */
    fun getMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
/**
 * Check if has WRITE_SECURE_SETTINGS permission
 * Note: This cannot be requested via runtime permission, must be granted via ADB
 */
    /**
     * Check if has WRITE_SECURE_SETTINGS permission
     */
    fun hasWriteSettingsPermission(context: Context): Boolean {
        return try {
            // Check via PackageManager
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.WRITE_SECURE_SETTINGS"
            ) == PackageManager.PERMISSION_GRANTED
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WRITE_SECURE_SETTINGS", e)
            false
        }
    }

    
    /**
     * Check if can switch SIM (has all necessary permissions)
     */
    fun canSwitchSIM(context: Context): Boolean {
        return hasRequiredPermissions(context) && hasWriteSettingsPermission(context)
    }
    
    /**
     * Get instructions untuk grant WRITE_SECURE_SETTINGS permission
     */
    fun getPermissionInstructions(packageName: String): String {
        return """
            |To enable automatic SIM switching, you need to grant WRITE_SECURE_SETTINGS permission.
            |
            |This permission cannot be requested via the app. Please follow these steps:
            |
            |1. Enable USB Debugging on your device:
            |   - Go to Settings > About Phone
            |   - Tap "Build Number" 7 times to enable Developer Options
            |   - Go to Settings > Developer Options
            |   - Enable "USB Debugging"
            |
            |2. Connect your device to a computer via USB
            |
            |3. Run this ADB command on your computer:
            |   adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS
            |
            |4. Restart the app
            |
            |Note: This only needs to be done once. The permission will persist until you uninstall the app.
        """.trimMargin()
    }
    
    /**
     * Get short permission instructions
     */
    fun getShortPermissionInstructions(packageName: String): String {
        return "Run on PC: adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
    }
    
    /**
     * Get permission status summary
     */
    fun getPermissionStatus(context: Context): Map<String, Boolean> {
        return mapOf(
            "READ_PHONE_STATE" to (ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED),
            
            "ACCESS_NETWORK_STATE" to (ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_NETWORK_STATE
            ) == PackageManager.PERMISSION_GRANTED),
            
            "READ_PHONE_NUMBERS" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.READ_PHONE_NUMBERS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not required on older versions
            },
            
            "WRITE_SECURE_SETTINGS" to hasWriteSettingsPermission(context)
        )
    }
}
