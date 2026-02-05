package com.switch_simcard_detection.adpstore.switch_simcard_detection

import androidx.annotation.NonNull
import android.content.Context
import android.util.Log

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.EventChannel

/** SwitchSimcardDetectionPlugin */
class SwitchSimcardDetectionPlugin: FlutterPlugin, MethodCallHandler {
  companion object {
    private const val TAG = "SwitchSimcardPlugin"
    private const val METHOD_CHANNEL = "switch_simcard_detection"
    private const val EVENT_CHANNEL = "switch_simcard_detection/events"
  }

  private lateinit var methodChannel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private lateinit var context: Context
  
  private var simSwitcher: SIMSwitcher? = null
  private var networkMonitor: NetworkMonitor? = null
  private var eventSink: EventChannel.EventSink? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    
    // Setup method channel
    methodChannel = MethodChannel(flutterPluginBinding.binaryMessenger, METHOD_CHANNEL)
    methodChannel.setMethodCallHandler(this)
    
    // Setup event channel for SIM switch events
    eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, EVENT_CHANNEL)
    eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
      override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        Log.d(TAG, "Event channel listener attached")
      }

      override fun onCancel(arguments: Any?) {
        eventSink = null
        Log.d(TAG, "Event channel listener cancelled")
      }
    })
    
    // Initialize SIMSwitcher
    simSwitcher = SIMSwitcher(context)
    
    Log.i(TAG, "Plugin attached to engine")
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    try {
      when (call.method) {
        "getPlatformVersion" -> {
          result.success("Android ${android.os.Build.VERSION.RELEASE}")
        }
        
        "getCurrentDataSIM" -> {
          handleGetCurrentDataSIM(result)
        }
        
        "switchDataSIM" -> {
          val simIndex = call.argument<Int>("simIndex")
          if (simIndex == null) {
            result.error("INVALID_ARGUMENT", "simIndex is required", null)
            return
          }
          handleSwitchDataSIM(simIndex, result)
        }
        
        "getSIMStatus" -> {
          handleGetSIMStatus(result)
        }
        
        "enableAutoSwitch" -> {
          val primarySIM = call.argument<Int>("primarySIM")
          val fallbackSIM = call.argument<Int>("fallbackSIM")
          
          if (primarySIM == null || fallbackSIM == null) {
            result.error("INVALID_ARGUMENT", "primarySIM and fallbackSIM are required", null)
            return
          }
          
          handleEnableAutoSwitch(primarySIM, fallbackSIM, result)
        }
        
        "disableAutoSwitch" -> {
          handleDisableAutoSwitch(result)
        }
        
        "checkPermissions" -> {
          handleCheckPermissions(result)
        }
        
        "getPermissionInstructions" -> {
          handleGetPermissionInstructions(result)
        }
        
        "canSwitchSIM" -> {
          handleCanSwitchSIM(result)
        }
        
        "getNetworkQuality" -> {
          handleGetNetworkQuality(result)
        }
        
        "getNetworkInfo" -> {
          handleGetNetworkInfo(result)
        }
        
        "isDeviceRooted" -> {
          handleIsDeviceRooted(result)
        }
        
        else -> {
          result.notImplemented()
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling method call: ${call.method}", e)
      result.error("ERROR", e.message, e.stackTraceToString())
    }
  }

  /**
   * Get current active data SIM
   */
  private fun handleGetCurrentDataSIM(result: Result) {
    try {
      val currentSIM = simSwitcher?.getCurrentDataSIM() ?: -1
      
      if (currentSIM == -1) {
        result.error("ERROR", "Failed to get current data SIM", null)
      } else {
        Log.d(TAG, "Current data SIM: ${currentSIM + 1}")
        result.success(currentSIM)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error getting current data SIM", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Switch to specific SIM
   */
  private fun handleSwitchDataSIM(simIndex: Int, result: Result) {
    try {
      // Validate input
      if (simIndex !in 0..1) {
        result.error("INVALID_ARGUMENT", "simIndex must be 0 or 1", null)
        return
      }
      
      // Check permissions
      if (!PermissionHelper.canSwitchSIM(context)) {
        result.error(
          "PERMISSION_DENIED",
          "Missing required permissions. Use checkPermissions() for details.",
          null
        )
        return
      }
      
      Log.i(TAG, "Switching to SIM ${simIndex + 1}")
      
      // Perform switch
      val success = simSwitcher?.smartSwitch(simIndex) ?: false
      
      if (success) {
        Log.i(TAG, "Successfully switched to SIM ${simIndex + 1}")
        
        // Send event to Flutter
        eventSink?.success(mapOf(
          "event" to "simSwitched",
          "simIndex" to simIndex,
          "timestamp" to System.currentTimeMillis()
        ))
        
        result.success(true)
      } else {
        Log.e(TAG, "Failed to switch to SIM ${simIndex + 1}")
        result.error("SWITCH_FAILED", "Failed to switch SIM", null)
      }
      
    } catch (e: Exception) {
      Log.e(TAG, "Error switching SIM", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Get status of all SIM cards
   */
  private fun handleGetSIMStatus(result: Result) {
    try {
      val status = simSwitcher?.getAllSIMStatus() ?: emptyMap()
      
      Log.d(TAG, "SIM Status: $status")
      result.success(status)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error getting SIM status", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Enable automatic SIM switching
   */
  private fun handleEnableAutoSwitch(primarySIM: Int, fallbackSIM: Int, result: Result) {
    try {
      // Validate input
      if (primarySIM !in 0..1 || fallbackSIM !in 0..1) {
        result.error("INVALID_ARGUMENT", "SIM indices must be 0 or 1", null)
        return
      }
      
      if (primarySIM == fallbackSIM) {
        result.error("INVALID_ARGUMENT", "Primary and fallback SIM must be different", null)
        return
      }
      
      // Check permissions
      if (!PermissionHelper.hasRequiredPermissions(context)) {
        result.error(
          "PERMISSION_DENIED",
          "Missing required permissions for network monitoring",
          null
        )
        return
      }
      
      Log.i(TAG, "Enabling auto-switch: Primary=SIM${primarySIM + 1}, Fallback=SIM${fallbackSIM + 1}")
      
      // Create network monitor if not exists
      if (networkMonitor == null) {
        networkMonitor = NetworkMonitor(
          context,
          onNetworkLost = { lostSIM ->
            Log.w(TAG, "Network lost on SIM${lostSIM + 1}, switching to fallback")
            
            val targetSIM = if (lostSIM == primarySIM) fallbackSIM else primarySIM
            val success = simSwitcher?.smartSwitch(targetSIM) ?: false
            
            if (success) {
              eventSink?.success(mapOf(
                "event" to "autoSwitched",
                "fromSIM" to lostSIM,
                "toSIM" to targetSIM,
                "reason" to "networkLost",
                "timestamp" to System.currentTimeMillis()
              ))
            }
          },
          onNetworkRestored = { restoredSIM ->
            Log.i(TAG, "Network restored on SIM${restoredSIM + 1}")
            
            eventSink?.success(mapOf(
              "event" to "networkRestored",
              "simIndex" to restoredSIM,
              "timestamp" to System.currentTimeMillis()
            ))
          }
        )
      }
      
      // Start monitoring
      networkMonitor?.startMonitoring(primarySIM, fallbackSIM)
      
      result.success(true)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error enabling auto-switch", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Disable automatic SIM switching
   */
  private fun handleDisableAutoSwitch(result: Result) {
    try {
      Log.i(TAG, "Disabling auto-switch")
      
      networkMonitor?.stopMonitoring()
      
      result.success(true)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error disabling auto-switch", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Check all permissions
   */
  private fun handleCheckPermissions(result: Result) {
    try {
      val status = PermissionHelper.getPermissionStatus(context)
      
      Log.d(TAG, "Permission status: $status")
      result.success(status)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error checking permissions", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Get permission instructions
   */
  private fun handleGetPermissionInstructions(result: Result) {
    try {
      val packageName = context.packageName
      val instructions = PermissionHelper.getPermissionInstructions(packageName)
      
      result.success(instructions)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error getting permission instructions", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Check if can switch SIM
   */
  private fun handleCanSwitchSIM(result: Result) {
    try {
      val canSwitch = PermissionHelper.canSwitchSIM(context)
      
      Log.d(TAG, "Can switch SIM: $canSwitch")
      result.success(canSwitch)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error checking if can switch SIM", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Get network quality
   */
  private fun handleGetNetworkQuality(result: Result) {
    try {
      if (networkMonitor == null) {
        networkMonitor = NetworkMonitor(context, {}, {})
      }
      
      val quality = networkMonitor?.getNetworkQuality()?.name ?: "UNKNOWN"
      
      Log.d(TAG, "Network quality: $quality")
      result.success(quality)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error getting network quality", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Get detailed network info
   */
  private fun handleGetNetworkInfo(result: Result) {
    try {
      if (networkMonitor == null) {
        networkMonitor = NetworkMonitor(context, {}, {})
      }
      
      val info = networkMonitor?.getNetworkInfo() ?: emptyMap()
      
      Log.d(TAG, "Network info: $info")
      result.success(info)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error getting network info", e)
      result.error("ERROR", e.message, null)
    }
  }

  /**
   * Check if device is rooted
   */
  private fun handleIsDeviceRooted(result: Result) {
    try {
      val isRooted = simSwitcher?.isDeviceRooted() ?: false
      
      Log.d(TAG, "Device rooted: $isRooted")
      result.success(isRooted)
      
    } catch (e: Exception) {
      Log.e(TAG, "Error checking if device is rooted", e)
      result.error("ERROR", e.message, null)
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    // Stop monitoring
    networkMonitor?.stopMonitoring()
    
    // Clean up
    methodChannel.setMethodCallHandler(null)
    eventSink = null
    
    Log.i(TAG, "Plugin detached from engine")
  }
}
