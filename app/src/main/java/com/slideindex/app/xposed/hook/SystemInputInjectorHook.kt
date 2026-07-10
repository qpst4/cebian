package com.slideindex.app.xposed.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.slideindex.app.autofill.OtpAutoInputBroadcastContract
import com.slideindex.app.xposed.XposedLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

class SystemInputInjectorHook {
  private var registered = false
  private var registerAttempts = 0
  private var amsSystemReadyHooked = false
  private var broadcastHooked = false
  private var lastAutoInputCallerUid = -1
  private var lastAutoInputCallerUidAt = 0L
  private val mainHandler = Handler(Looper.getMainLooper())
  private var inputManagerInstance: Any? = null
  private var injectMethod: Method? = null
  private var injectMethodParamCount = 0

  fun install(classLoader: ClassLoader) {
    runCatching {
      XposedLog.i(TAG, "Installing system input injector in system_server")
      hookBroadcastCallerUid(classLoader)
      hookAmsSystemReadyFallback(classLoader)
      val systemContext = resolveSystemContext(classLoader)
      if (systemContext != null) {
        scheduleRegister(systemContext, classLoader)
      } else {
        XposedLog.w(TAG, "System context unavailable, waiting for systemReady")
      }
    }.onFailure { XposedLog.e(TAG, "SystemInputInjectorHook failed", it) }
  }

  private fun hookBroadcastCallerUid(classLoader: ClassLoader) {
    if (broadcastHooked) return
    val queueClass = runCatching {
      XposedHelpers.findClass("com.android.server.am.BroadcastQueueImpl", classLoader)
    }.getOrNull() ?: runCatching {
      XposedHelpers.findClass("com.android.server.am.BroadcastQueue", classLoader)
    }.getOrNull() ?: return
    val methods = queueClass.declaredMethods.filter { it.name == "enqueueBroadcastLocked" }
    if (methods.isEmpty()) {
      Log.w(TAG, "BroadcastQueue.enqueueBroadcastLocked not found")
      return
    }
    for (method in methods) {
      XposedBridge.hookMethod(method, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          val record = param.args.firstOrNull() ?: return
          val intent = extractBroadcastIntent(record) ?: return
          if (intent.action != OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT) return
          val (callerUid, callerPkg) = extractCallerInfo(record)
          if (callerUid >= 0) {
            lastAutoInputCallerUid = callerUid
            lastAutoInputCallerUidAt = SystemClock.elapsedRealtime()
          }
          Log.d(TAG, "Broadcast enqueue: callerUid=$callerUid callerPkg=$callerPkg")
        }
      })
    }
    broadcastHooked = true
    XposedLog.i(TAG, "Hooked BroadcastQueue.enqueueBroadcastLocked for caller uid")
  }

  private fun extractBroadcastIntent(record: Any): Intent? {
    if (record is Intent) return record
    runCatching {
      val intent = XposedHelpers.getObjectField(record, "intent")
      if (intent is Intent) return intent
    }
    runCatching {
      val intent = XposedHelpers.callMethod(record, "getIntent")
      if (intent is Intent) return intent
    }
    return null
  }

  private fun extractCallerInfo(record: Any): Pair<Int, String?> {
    for (fieldName in listOf("callerUid", "callingUid", "uid")) {
      runCatching {
        val uid = XposedHelpers.getIntField(record, fieldName)
        if (uid >= 0) {
          val pkg = runCatching {
            XposedHelpers.getObjectField(record, "callerPackage") as? String
          }.getOrNull()
          return uid to pkg
        }
      }
    }
    runCatching {
      val callerApp = XposedHelpers.getObjectField(record, "callerApp") ?: return -1 to null
      val uid = XposedHelpers.getIntField(callerApp, "uid")
      if (uid >= 0) return uid to null
    }
    return -1 to null
  }

  private fun resolveSendingUid(receiver: BroadcastReceiver): Int {
    val cachedUid = lastAutoInputCallerUid
    if (cachedUid >= 0) {
      val ageMs = SystemClock.elapsedRealtime() - lastAutoInputCallerUidAt
      if (ageMs in 0..CACHED_UID_TTL_MS) return cachedUid
    }
    if (Build.VERSION.SDK_INT >= 34) {
      runCatching {
        val uid = XposedHelpers.callMethod(receiver, "getSendingUid") as? Int
        if (uid != null && uid >= 0) return uid
      }
    }
    return -1
  }

  private fun resolvePackagesForUid(context: Context, uid: Int): Set<String> {
    if (uid < 0) return emptySet()
    return context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty()
  }

  private fun shouldAllowAutoInputSender(
    sendingUid: Int,
    systemContextUid: Int,
    senderPackages: Set<String>,
  ): Boolean {
    if (sendingUid == -1) return true
    if (sendingUid == 1000 || sendingUid == 1001 || sendingUid == systemContextUid) return true
    if (senderPackages.contains(MODULE_PACKAGE)) return true
    if (senderPackages.any { it in TRUSTED_SENDER_PACKAGES }) return true
    return false
  }

  private fun resolveSystemContext(classLoader: ClassLoader): Context? =
    runCatching {
      val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader)
      val currentActivityThread =
        XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread")
      XposedHelpers.callMethod(currentActivityThread, "getSystemContext") as Context
    }.getOrNull()

  private fun hookAmsSystemReadyFallback(classLoader: ClassLoader) {
    if (amsSystemReadyHooked) return
    val amsClass = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader)
    val methods = amsClass.declaredMethods.filter { it.name == "systemReady" }
    if (methods.isEmpty()) {
      Log.w(TAG, "ActivityManagerService.systemReady not found")
      return
    }
    for (method in methods) {
      XposedBridge.hookMethod(method, object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
          val systemContext = resolveSystemContext(classLoader) ?: return
          XposedLog.i(TAG, "systemReady fired, scheduling receiver registration")
          scheduleRegister(systemContext, classLoader)
        }
      })
    }
    amsSystemReadyHooked = true
    XposedLog.i(TAG, "Hooked ActivityManagerService.systemReady fallback")
  }

  private fun scheduleRegister(context: Context, classLoader: ClassLoader) {
    if (registered) return
    mainHandler.postDelayed({
      runCatching { registerReceiver(context, classLoader) }
        .onFailure { XposedLog.e(TAG, "registerReceiver attempt failed", it) }
      if (!registered && registerAttempts < MAX_REGISTER_ATTEMPTS) {
        scheduleRegister(context, classLoader)
      } else if (!registered) {
        XposedLog.e(TAG, "Giving up receiver registration after $registerAttempts attempts")
      }
    }, DELAY_REGISTER_MS)
  }

  private fun registerReceiver(context: Context, classLoader: ClassLoader) {
    if (registered) return
    registerAttempts++
    if (!resolveInputInjector(classLoader)) {
      Log.w(TAG, "Input injector unresolved on attempt $registerAttempts")
      return
    }
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_PROBE, false)) {
          val attemptId = intent.getLongExtra(OtpAutoInputBroadcastContract.EXTRA_ATTEMPT_ID, 0L)
          sendAutoInputResult(context, attemptId, true, "probe")
          if (isOrderedBroadcast) abortBroadcast()
          XposedLog.i(TAG, "Probe OK: system inject receiver is active in system_server")
          return
        }
        if (!intent.getBooleanExtra(OtpAutoInputBroadcastContract.EXTRA_ALLOW_SYSTEM_INJECT, true)) {
          Log.d(TAG, "System inject disabled by app setting, skipping")
          return
        }
        if (isOrderedBroadcast && resultCode != 0) return
        val sendingUid = resolveSendingUid(this)
        val senderPackages = resolvePackagesForUid(context, sendingUid)
        if (!shouldAllowAutoInputSender(sendingUid, context.applicationInfo.uid, senderPackages)) {
          Log.w(
            TAG,
            "Rejected auto-input from uid=$sendingUid packages=${senderPackages.joinToString()}",
          )
          return
        }
        val request = OtpAutoInputBroadcastContract.readRequest(intent) ?: return
        Log.i(
          TAG,
          "System inject request: codeLen=${request.code.length} autoEnter=${request.autoEnter}",
        )
        XposedLog.i(TAG, "System inject request codeLen=${request.code.length}")
        val interval = request.inputIntervalMs.coerceAtMost(MAX_SYNC_INPUT_INTERVAL_MS)
        val result = performInjectText(request.code, request.autoEnter, interval, classLoader)
        sendAutoInputResult(context, request.attemptId, result.success, result.reason)
        if (result.success) {
          abortBroadcast()
          XposedLog.i(TAG, "System inject success, aborting accessibility fallback")
        } else {
          Log.w(TAG, "System inject failed (${result.reason}), allowing accessibility fallback")
        }
      }
    }
    val filter = IntentFilter(OtpAutoInputBroadcastContract.ACTION_AUTO_INPUT).apply {
      priority = OtpAutoInputBroadcastContract.RECEIVER_PRIORITY_SYSTEM
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
      @Suppress("DEPRECATION")
      context.registerReceiver(receiver, filter)
    }
    registered = true
    XposedLog.i(TAG, "SystemInputInjectorHook receiver registered in system_server (process ready)")
  }

  private data class InjectResult(val success: Boolean, val reason: String)

  private fun performInjectText(
    text: String,
    autoEnter: Boolean,
    intervalMs: Long,
    classLoader: ClassLoader,
  ): InjectResult {
    if (!resolveInputInjector(classLoader)) {
      return InjectResult(false, "manager_unresolved")
    }
    val manager = inputManagerInstance ?: return InjectResult(false, "manager_unresolved")
    val method = injectMethod ?: return InjectResult(false, "inject_method_unresolved")
    val mode = resolveInjectMode(classLoader)
    return runCatching {
      val keyMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
      var injectedCount = 0
      for (ch in text) {
        val events = keyMap.getEvents(charArrayOf(ch)) ?: continue
        for (event in events) {
          event.source = InputDeviceSourceKeyboard
          if (invokeInject(manager, method, event, mode)) {
            injectedCount++
          }
          if (intervalMs > 0) Thread.sleep(intervalMs)
        }
      }
      if (autoEnter) {
        val now = SystemClock.uptimeMillis()
        injectKeyEvent(manager, method, mode, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, now)
        injectKeyEvent(manager, method, mode, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, now)
      }
      if (injectedCount > 0 || autoEnter) {
        InjectResult(true, "ok")
      } else {
        InjectResult(false, "no_key_events")
      }
    }.getOrElse {
      Log.e(TAG, "performInjectText failed", it)
      InjectResult(false, "inject_exception")
    }
  }

  private fun injectKeyEvent(
    manager: Any,
    method: Method,
    mode: Int,
    action: Int,
    keyCode: Int,
    eventTime: Long,
  ) {
    val event = KeyEvent(eventTime, eventTime, action, keyCode, 0, 0, -1, 0, 0, InputDeviceSourceKeyboard)
    invokeInject(manager, method, event, mode)
  }

  private fun invokeInject(manager: Any, method: Method, event: InputEvent, mode: Int): Boolean {
    val result = when (injectMethodParamCount) {
      2 -> method.invoke(manager, event, mode)
      3 -> method.invoke(manager, event, mode, 0)
      else -> return false
    }
    return when (result) {
      is Boolean -> result
      is Number -> result.toInt() != 0
      else -> true
    }
  }

  private fun resolveInputInjector(classLoader: ClassLoader): Boolean {
    if (inputManagerInstance != null && injectMethod != null) return true
    val classNames = if (Build.VERSION.SDK_INT >= 34) {
      listOf("android.hardware.input.InputManagerGlobal", "android.hardware.input.InputManager")
    } else {
      listOf("android.hardware.input.InputManager", "android.hardware.input.InputManagerGlobal")
    }
    for (className in classNames) {
      val managerClass = runCatching { XposedHelpers.findClass(className, classLoader) }.getOrNull()
        ?: continue
      val instance = resolveManagerInstance(managerClass) ?: continue
      val method = findInjectMethod(managerClass) ?: continue
      inputManagerInstance = instance
      injectMethod = method
      injectMethodParamCount = method.parameterTypes.size
      XposedLog.i(TAG, "Resolved input injector via $className (${method.name}, params=$injectMethodParamCount)")
      return true
    }
    return false
  }

  private fun resolveManagerInstance(managerClass: Class<*>): Any? {
    runCatching {
      val getInstance = XposedHelpers.findMethodExactIfExists(managerClass, "getInstance")
      if (getInstance != null) {
        return XposedHelpers.callStaticMethod(managerClass, "getInstance")
      }
    }
    runCatching {
      val getInstance = managerClass.getMethod("getInstance")
      return getInstance.invoke(null)
    }
    return null
  }

  private fun findInjectMethod(managerClass: Class<*>): Method? {
    val candidates = (managerClass.declaredMethods + managerClass.methods)
      .distinctBy { "${it.name}#${it.parameterTypes.joinToString()}" }
      .filter { it.name == "injectInputEvent" }
    val twoArg = candidates.firstOrNull {
      val params = it.parameterTypes
      params.size == 2 &&
        InputEvent::class.java.isAssignableFrom(params[0]) &&
        params[1] == Int::class.javaPrimitiveType
    }
    if (twoArg != null) {
      twoArg.isAccessible = true
      return twoArg
    }
    val threeArg = candidates.firstOrNull {
      val params = it.parameterTypes
      params.size == 3 &&
        InputEvent::class.java.isAssignableFrom(params[0]) &&
        params[1] == Int::class.javaPrimitiveType &&
        params[2] == Int::class.javaPrimitiveType
    }
    threeArg?.isAccessible = true
    return threeArg
  }

  private fun resolveInjectMode(classLoader: ClassLoader): Int {
    val managerClassNames = listOf(
      "android.hardware.input.InputManager",
      "android.hardware.input.InputManagerGlobal",
    )
    for (className in managerClassNames) {
      val mode = runCatching {
        XposedHelpers.getStaticIntField(
          XposedHelpers.findClass(className, classLoader),
          "INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH",
        )
      }.getOrNull()
      if (mode != null) return mode
    }
    return 2
  }

  private fun sendAutoInputResult(
    context: Context,
    attemptId: Long,
    success: Boolean,
    reason: String,
  ) {
    context.sendBroadcast(
      OtpAutoInputBroadcastContract.buildResultIntent(attemptId, success, "system_inject", reason),
    )
  }

  companion object {
    private const val TAG = "SystemInputInjector"
    private const val MODULE_PACKAGE = "com.slideindex.app"
    private const val DELAY_REGISTER_MS = 500L
    private const val MAX_REGISTER_ATTEMPTS = 10
    private const val MAX_SYNC_INPUT_INTERVAL_MS = 200L
    private const val CACHED_UID_TTL_MS = 10_000L
    private const val InputDeviceSourceKeyboard = android.view.InputDevice.SOURCE_KEYBOARD
    private val TRUSTED_SENDER_PACKAGES = setOf(
      "com.android.phone",
      "com.android.providers.telephony",
      "com.android.mms",
    )
  }
}
