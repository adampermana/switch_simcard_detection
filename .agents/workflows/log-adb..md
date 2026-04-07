---
description: result debug console
---

D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
D/SwitchSimcardPlugin( 7992): Current data SIM: 1
D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
E/SIMSwitcher( 7992): Error getting SIM status
E/SIMSwitcher( 7992): java.lang.SecurityException: access global settings MULTI_SIM_DATA_CALL_SUBSCRIPTION: Neither user 10115 nor current process has android.permission.READ_PRIVILEGED_PHONE_STATE.
E/SIMSwitcher( 7992): 	at android.os.Parcel.createExceptionOrNull(Parcel.java:2425)
E/SIMSwitcher( 7992): 	at android.os.Parcel.createException(Parcel.java:2409)
E/SIMSwitcher( 7992): 	at android.os.Parcel.readException(Parcel.java:2392)
E/SIMSwitcher( 7992): 	at android.database.DatabaseUtils.readExceptionFromParcel(DatabaseUtils.java:190)
E/SIMSwitcher( 7992): 	at android.database.DatabaseUtils.readExceptionFromParcel(DatabaseUtils.java:142)
E/SIMSwitcher( 7992): 	at android.content.ContentProviderProxy.call(ContentProviderNative.java:732)
E/SIMSwitcher( 7992): 	at android.provider.Settings$NameValueCache.getStringForUser(Settings.java:2971)
E/SIMSwitcher( 7992): 	at android.provider.Settings$Global.getStringForUser(Settings.java:15142)
E/SIMSwitcher( 7992): 	at android.provider.Settings$Global.getString(Settings.java:15130)
E/SIMSwitcher( 7992): 	at android.provider.Settings$Global.getInt(Settings.java:15335)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SIMSwitcher.getAllSIMStatus(SIMSwitcher.kt:474)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SwitchSimcardDetectionPlugin.handleGetSIMStatus(SwitchSimcardDetectionPlugin.kt:203)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SwitchSimcardDetectionPlugin.onMethodCall(SwitchSimcardDetectionPlugin.kt:78)
E/SIMSwitcher( 7992): 	at io.flutter.plugin.common.MethodChannel$IncomingMethodCallHandler.onMessage(MethodChannel.java:267)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger.invokeHandler(DartMessenger.java:292)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger.lambda$dispatchMessageToQueue$0$io-flutter-embedding-engine-dart-DartMessenger(DartMessenger.java:319)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger$$ExternalSyntheticLambda0.run(D8$$SyntheticClass:0)
E/SIMSwitcher( 7992): 	at android.os.Handler.handleCallback(Handler.java:938)
E/SIMSwitcher( 7992): 	at android.os.Handler.dispatchMessage(Handler.java:99)
E/SIMSwitcher( 7992): 	at android.os.Looper.loopOnce(Looper.java:201)
E/SIMSwitcher( 7992): 	at android.os.Looper.loop(Looper.java:288)
E/SIMSwitcher( 7992): 	at android.app.ActivityThread.main(ActivityThread.java:7880)
E/SIMSwitcher( 7992): 	at java.lang.reflect.Method.invoke(Native Method)
E/SIMSwitcher( 7992): 	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:548)
E/SIMSwitcher( 7992): 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1009)
E/SIMSwitcher( 7992): Caused by: android.os.RemoteException: Remote stack trace:
E/SIMSwitcher( 7992): 	at android.app.ContextImpl.enforce(ContextImpl.java:2178)
E/SIMSwitcher( 7992): 	at android.app.ContextImpl.enforceCallingOrSelfPermission(ContextImpl.java:2206)
E/SIMSwitcher( 7992): 	at com.android.providers.settings.SettingsProvider.enforceSettingReadable(SettingsProvider.java:2100)
E/SIMSwitcher( 7992): 	at com.android.providers.settings.SettingsProvider.getGlobalSetting(SettingsProvider.java:1354)
E/SIMSwitcher( 7992): 	at com.android.providers.settings.SettingsProvider.call(SettingsProvider.java:417)
E/SIMSwitcher( 7992):
D/SwitchSimcardPlugin( 7992): SIM Status: {currentSlot=0, currentSIM=1}
D/SwitchSimcardPlugin( 7992): Permission status: {READ_PHONE_STATE=true, ACCESS_NETWORK_STATE=true, CHANGE_NETWORK_STATE=true, READ_PHONE_NUMBERS=true, WRITE_SECURE_SETTINGS=true}
D/SwitchSimcardPlugin( 7992): Can switch SIM: true
D/SwitchSimcardPlugin( 7992): Network quality: EXCELLENT
D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
D/SIMSwitcher( 7992): Active SIM slots: [0, 1]
D/SwitchSimcardPlugin( 7992): Network info: {hasNetwork=true, hasInternet=true, isValidated=true, quality=EXCELLENT, downSpeed=30000, upSpeed=15000, signalLevel=4, currentSlot=0, currentSIM=1, activeSlots=0,1}
D/SwitchSimcardPlugin( 7992): Device rooted: false
I/SwitchSimcardPlugin( 7992): Switching to SIM 2
I/SIMSwitcher( 7992): === Smart Switch to SIM slot 1 ===
D/SIMSwitcher( 7992): SIM slot 1 active: true
D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
D/SIMSwitcher( 7992): Attempt 1 (Android 12+): SubscriptionManager hidden API
D/SIMSwitcher( 7992): Switching to slot 1 (subId: 5) via API
E/SIMSwitcher( 7992): API method failed
E/SIMSwitcher( 7992): java.lang.reflect.InvocationTargetException
E/SIMSwitcher( 7992): 	at java.lang.reflect.Method.invoke(Native Method)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SIMSwitcher.switchViaSubscriptionManager(SIMSwitcher.kt:275)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SIMSwitcher.smartSwitch(SIMSwitcher.kt:363)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SwitchSimcardDetectionPlugin.handleSwitchDataSIM(SwitchSimcardDetectionPlugin.kt:174)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SwitchSimcardDetectionPlugin.onMethodCall(SwitchSimcardDetectionPlugin.kt:74)
E/SIMSwitcher( 7992): 	at io.flutter.plugin.common.MethodChannel$IncomingMethodCallHandler.onMessage(MethodChannel.java:267)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger.invokeHandler(DartMessenger.java:292)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger.lambda$dispatchMessageToQueue$0$io-flutter-embedding-engine-dart-DartMessenger(DartMessenger.java:319)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger$$ExternalSyntheticLambda0.run(D8$$SyntheticClass:0)
E/SIMSwitcher( 7992): 	at android.os.Handler.handleCallback(Handler.java:938)
E/SIMSwitcher( 7992): 	at android.os.Handler.dispatchMessage(Handler.java:99)
E/SIMSwitcher( 7992): 	at android.os.Looper.loopOnce(Looper.java:201)
E/SIMSwitcher( 7992): 	at android.os.Looper.loop(Looper.java:288)
E/SIMSwitcher( 7992): 	at android.app.ActivityThread.main(ActivityThread.java:7880)
E/SIMSwitcher( 7992): 	at java.lang.reflect.Method.invoke(Native Method)
E/SIMSwitcher( 7992): 	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:548)
E/SIMSwitcher( 7992): 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1009)
E/SIMSwitcher( 7992): Caused by: java.lang.SecurityException: setDefaultDataSubId: Neither user 10115 nor current process has android.permission.MODIFY_PHONE_STATE.
E/SIMSwitcher( 7992): 	at android.os.Parcel.createExceptionOrNull(Parcel.java:2425)
E/SIMSwitcher( 7992): 	at android.os.Parcel.createException(Parcel.java:2409)
E/SIMSwitcher( 7992): 	at android.os.Parcel.readException(Parcel.java:2392)
E/SIMSwitcher( 7992): 	at android.os.Parcel.readException(Parcel.java:2334)
E/SIMSwitcher( 7992): 	at com.android.internal.telephony.ISub$Stub$Proxy.setDefaultDataSubId(ISub.java:2235)
E/SIMSwitcher( 7992): 	at android.telephony.SubscriptionManager.setDefaultDataSubId(SubscriptionManager.java:2208)
E/SIMSwitcher( 7992): 	... 17 more
D/SIMSwitcher( 7992): Attempt 2: Settings/TelephonyManager API
D/SIMSwitcher( 7992): Slot 1 -> SubId 5
D/SIMSwitcher( 7992): Switching to slot 1 (subId: 5) via Settings/API
D/SIMSwitcher( 7992): setPreferredDataSubscriptionId not available on this device
I/SIMSwitcher( 7992): √ Set multi_sim_data_call = 5
D/SIMSwitcher( 7992): Key 'multi_sim_data_call' error: access global settings MULTI_SIM_DATA_CALL_SUBSCRIPTION: Neither user 10115 nor current process has android.permission.READ_PRIVILEGED_PHONE_STATE.
I/SIMSwitcher( 7992): √ Set user_preferred_data_sub = 5
I/SIMSwitcher( 7992): √ Verified: user_preferred_data_sub = 5
I/SIMSwitcher( 7992): √ Switch successful via Settings/TelephonyManager
I/SwitchSimcardPlugin( 7992): Successfully switched to SIM 2
D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
D/SwitchSimcardPlugin( 7992): Current data SIM: 1
D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
D/SwitchSimcardPlugin( 7992): Current data SIM: 1
D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
D/SwitchSimcardPlugin( 7992): Current data SIM: 1
D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
E/SIMSwitcher( 7992): Error getting SIM status
E/SIMSwitcher( 7992): java.lang.SecurityException: access global settings MULTI_SIM_DATA_CALL_SUBSCRIPTION: Neither user 10115 nor current process has android.permission.READ_PRIVILEGED_PHONE_STATE.
E/SIMSwitcher( 7992): 	at android.os.Parcel.createExceptionOrNull(Parcel.java:2425)
E/SIMSwitcher( 7992): 	at android.os.Parcel.createException(Parcel.java:2409)
E/SIMSwitcher( 7992): 	at android.os.Parcel.readException(Parcel.java:2392)
E/SIMSwitcher( 7992): 	at android.database.DatabaseUtils.readExceptionFromParcel(DatabaseUtils.java:190)
E/SIMSwitcher( 7992): 	at android.database.DatabaseUtils.readExceptionFromParcel(DatabaseUtils.java:142)
E/SIMSwitcher( 7992): 	at android.content.ContentProviderProxy.call(ContentProviderNative.java:732)
E/SIMSwitcher( 7992): 	at android.provider.Settings$NameValueCache.getStringForUser(Settings.java:2971)
E/SIMSwitcher( 7992): 	at android.provider.Settings$Global.getStringForUser(Settings.java:15142)
E/SIMSwitcher( 7992): 	at android.provider.Settings$Global.getString(Settings.java:15130)
E/SIMSwitcher( 7992): 	at android.provider.Settings$Global.getInt(Settings.java:15335)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SIMSwitcher.getAllSIMStatus(SIMSwitcher.kt:474)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SwitchSimcardDetectionPlugin.handleGetSIMStatus(SwitchSimcardDetectionPlugin.kt:203)
E/SIMSwitcher( 7992): 	at com.switch_simcard_detection.adpstore.switch_simcard_detection.SwitchSimcardDetectionPlugin.onMethodCall(SwitchSimcardDetectionPlugin.kt:78)
E/SIMSwitcher( 7992): 	at io.flutter.plugin.common.MethodChannel$IncomingMethodCallHandler.onMessage(MethodChannel.java:267)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger.invokeHandler(DartMessenger.java:292)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger.lambda$dispatchMessageToQueue$0$io-flutter-embedding-engine-dart-DartMessenger(DartMessenger.java:319)
E/SIMSwitcher( 7992): 	at io.flutter.embedding.engine.dart.DartMessenger$$ExternalSyntheticLambda0.run(D8$$SyntheticClass:0)
E/SIMSwitcher( 7992): 	at android.os.Handler.handleCallback(Handler.java:938)
E/SIMSwitcher( 7992): 	at android.os.Handler.dispatchMessage(Handler.java:99)
E/SIMSwitcher( 7992): 	at android.os.Looper.loopOnce(Looper.java:201)
E/SIMSwitcher( 7992): 	at android.os.Looper.loop(Looper.java:288)
E/SIMSwitcher( 7992): 	at android.app.ActivityThread.main(ActivityThread.java:7880)
E/SIMSwitcher( 7992): 	at java.lang.reflect.Method.invoke(Native Method)
E/SIMSwitcher( 7992): 	at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:548)
E/SIMSwitcher( 7992): 	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1009)
D/SwitchSimcardPlugin( 7992): SIM Status: {currentSlot=0, currentSIM=1}
D/SIMSwitcher( 7992): SubId 4 -> Slot 0
D/SIMSwitcher( 7992): Current data SIM: slot 0 (subId: 4)
E/SIMSwitcher( 7992): Error getting SIM status
E/SIMSwitcher( 7992): java.lang.SecurityException