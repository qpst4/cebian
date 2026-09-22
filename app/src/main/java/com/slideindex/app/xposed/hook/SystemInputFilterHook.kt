package com.slideindex.app.xposed.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.slideindex.app.xposed.HookParam
import com.slideindex.app.xposed.LibXposedMethodHook
import com.slideindex.app.xposed.LibXposedReflect
import com.slideindex.app.xposed.XposedLog
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import com.slideindex.app.xposed.hookMethod
import com.slideindex.app.xposed.takeover.SystemGestureTakeoverController
import com.slideindex.app.xposed.takeover.TakeoverSessionPolicy
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

/**
 * 输入层接管：hook `InputManagerService.filterInputEvent`（EdgeX 同款路径）。
 *
 * 状态通道（广播接收器）与 hook 安装完全解耦、优先建立：任何一步失败都能通过
 * 状态响应回传到 app，避免"看着装上了但没生效、又查不到原因"的黑盒。
 */
class SystemInputFilterHook {
  @Volatile
  private var systemContext: Context? = null

  @Volatile
  private var currentClassLoader: ClassLoader? = null

  @Volatile
  private var xposedInterface: XposedInterface? = null

  @Volatile
  private var controller: SystemGestureTakeoverController? = null

  @Volatile
  private var receiverRegistered = false

  @Volatile
  private var filterHookInstalled = false

  @Volatile
  private var enableHookInstalled = false

  @Volatile
  private var startHookInstalled = false

  @Volatile
  private var inputManagerService: Any? = null

  @Volatile
  private var enableApplier: ((Boolean) -> Unit)? = null

  @Volatile
  private var forwardingFilterInstalled = false

  /** 代理自身转发事件时的重入标记，避免同一事件被反复转发。 */
  private val forwardingInProgress = ThreadLocal.withInitial { false }

  private val moduleErrors: MutableList<String> = Collections.synchronizedList(mutableListOf())
  private val eventsSeen = AtomicLong()
  private val eventsSwallowed = AtomicLong()
  private val eventsPassed = AtomicLong()
  private var applyingEnable = false
  private val mainHandler = Handler(Looper.getMainLooper())

  /**
   * 建立状态通道（广播接收器）。
   *
   * `onSystemServerStarting` 阶段 ActivityManagerService 往往还没就绪，
   * 此时 `registerReceiver` 会因 IActivityManager 为 null 抛 NPE，因此按固定间隔重试。
   */
  fun registerStatusChannel(classLoader: ClassLoader) {
    currentClassLoader = classLoader
    if (receiverRegistered) return
    attemptReceiverRegistration(classLoader, attempt = 0)
  }

  private fun attemptReceiverRegistration(classLoader: ClassLoader, attempt: Int) {
    if (receiverRegistered) return
    runCatching {
      val context = systemContext ?: resolveSystemContext(classLoader)
        ?: throw IllegalStateException("system context unavailable")
      systemContext = context
      registerBridgeReceiver(context)
      receiverRegistered = true
    }.onSuccess {
      XposedLog.i(TAG, "Takeover status channel registered in system_server")
      // 注册成功后再补齐：模块可能错过了 app 启动时的配置广播。
      mainHandler.post {
        ensureController()?.let { controller ->
          controller.requestSnapshotIfNeeded()
          syncFilterEnabled(controller)
        }
      }
    }.onFailure { throwable ->
      if (attempt >= MAX_RECEIVER_REGISTER_ATTEMPTS) {
        recordModuleError(describe("receiver", throwable))
      } else {
        mainHandler.postDelayed(
          { attemptReceiverRegistration(classLoader, attempt + 1) },
          RECEIVER_REGISTER_RETRY_MS,
        )
      }
    }
  }

  fun recordModuleError(message: String) {
    moduleErrors.add(message)
    XposedLog.w(TAG, message)
  }

  fun install(xposed: XposedInterface, classLoader: ClassLoader): List<XposedInterface.HookHandle> {
    xposedInterface = xposed
    currentClassLoader = classLoader
    if (!receiverRegistered) {
      registerStatusChannel(classLoader)
    }
    val takeoverController = ensureController()
      ?: run {
        recordModuleError("controller: unavailable")
        return emptyList()
      }

    val handles = mutableListOf<XposedInterface.HookHandle>()
    val imsClass = LibXposedReflect.findClassIfExists(
      "com.android.server.input.InputManagerService",
      classLoader,
    )
    if (imsClass == null) {
      recordModuleError("ims: InputManagerService not found")
      return handles
    }
    if (!enableHookInstalled) {
      runCatching { handles += hookFilterEnable(xposed, imsClass, classLoader, takeoverController) }
        .onSuccess { enableHookInstalled = true }
        .onFailure { recordModuleError(describe("enableFilter", it)) }
    }
    if (!filterHookInstalled) {
      runCatching { handles += hookFilterInputEvent(xposed, imsClass, takeoverController) }
        .onSuccess { filterHookInstalled = true }
        .onFailure { recordModuleError(describe("filterInputEvent", it)) }
    }
    if (!startHookInstalled) {
      runCatching { handles += hookStart(xposed, imsClass, takeoverController) }
        .onSuccess { startHookInstalled = true }
        .onFailure { recordModuleError(describe("start", it)) }
    }
    XposedLog.i(
      TAG,
      "System input filter hook installed: proxy=$forwardingFilterInstalled " +
        "enable=$enableHookInstalled start=$startHookInstalled receiver=$receiverRegistered " +
        "hooks=${handles.size}",
    )
    return handles
  }

  /** 状态请求到达时按需重试未完成的步骤（例如首次安装部分失败）。 */
  private fun retryInstallIfNeeded() {
    if (filterHookInstalled && enableHookInstalled && startHookInstalled && receiverRegistered) return
    val xposed = xposedInterface ?: return
    val classLoader = currentClassLoader ?: return
    install(xposed, classLoader)
  }

  private fun ensureController(): SystemGestureTakeoverController? {
    controller?.let { return it }
    val context = systemContext ?: return null
    return SystemGestureTakeoverController(context) { message -> XposedLog.i(TAG, message) }
      .also { controller = it }
  }

  private fun describe(step: String, throwable: Throwable): String =
    "$step: ${throwable.javaClass.simpleName}: ${throwable.message}"

  private fun hookFilterEnable(
    xposed: XposedInterface,
    imsClass: Class<*>,
    classLoader: ClassLoader,
    takeoverController: SystemGestureTakeoverController,
  ): List<XposedInterface.HookHandle> {
    val handles = mutableListOf<XposedInterface.HookHandle>()
    handles += hookEnableLikeMethod(xposed, imsClass, takeoverController, "ims")
    handles += hookEnableLikeMethod(
      xposed,
      LibXposedReflect.findClassIfExists(
        "com.android.server.input.NativeInputManagerService\$NativeImpl",
        classLoader,
      ),
      takeoverController,
      "native",
    )

    // 关键：必须装一个过滤器，native 才会在"派发给手势监听通道之前"就咨询过滤器，
    // 否则SystemUI 的左右返回手势会绕过我们的丢弃（实测：不装过滤器时返回手势照样触发）。
    // 它只负责把未被接管的事件原样转发回输入管线。
    currentClassLoader?.let { ensureForwardingFilter(it) }

    return handles
  }

  /**
   * 第一层（已验证有效）：在 Java 回调入口丢弃被接管的事件。
   *
   * 该路径不依赖 `mInputFilter` 是谁——即使系统或无障碍把过滤器换掉，这里依然能拦住窗口派发。
   */
  private fun hookFilterInputEvent(
    xposed: XposedInterface,
    imsClass: Class<*>,
    takeoverController: SystemGestureTakeoverController,
  ): List<XposedInterface.HookHandle> {
    val methods = imsClass.declaredMethods.filter { it.name == FILTER_INPUT_EVENT }
    if (methods.isEmpty()) throw IllegalStateException("filterInputEvent not found")
    return methods.map { method ->
      xposed.hookMethod(
        method,
        object : LibXposedMethodHook() {
          override fun beforeHookedMethod(param: HookParam) {
            val event = param.arg(0) as? MotionEvent ?: return
            val policyFlags = (param.arg(1) as? Int) ?: 0
            eventsSeen.incrementAndGet()
            if (!takeoverController.handleEvent(event, policyFlags)) {
              eventsPassed.incrementAndGet()
              return
            }
            eventsSwallowed.incrementAndGet()
            param.returnEarly = true
            param.result = false
          }
        },
        id = "cebian_input_filter_event_${method.parameterTypes.joinToString { it.simpleName }}",
      )
    }
  }

  private fun hookEnableLikeMethod(
    xposed: XposedInterface,
    target: Class<*>?,
    takeoverController: SystemGestureTakeoverController,
    idSuffix: String,
  ): List<XposedInterface.HookHandle> {
    target ?: return emptyList()
    val methods = target.declaredMethods.filter { it.name == SET_INPUT_FILTER_ENABLED }
    return methods.map { method ->
      xposed.hookMethod(
        method,
        object : LibXposedMethodHook() {
          override fun afterHookedMethod(param: HookParam) {
            if (applyingEnable) return
            if (!takeoverController.hasEnabledGroups()) return
            // 期望开启时若被别的组件关掉，这里补回一次。
            applyFilterEnabled(true)
          }
        },
        id = "cebian_input_filter_enable_${idSuffix}_${
          method.parameterTypes.joinToString { it.simpleName }
        }",
      )
    }
  }

  private fun hookStart(
    xposed: XposedInterface,
    imsClass: Class<*>,
    takeoverController: SystemGestureTakeoverController,
  ): List<XposedInterface.HookHandle> {
    val methods = imsClass.declaredMethods.filter { it.name == "start" && it.parameterCount == 0 }
    return methods.map { method ->
      xposed.hookMethod(
        method,
        object : LibXposedMethodHook() {
          override fun afterHookedMethod(param: HookParam) {
            inputManagerService = param.thisObject
            mainHandler.post {
              takeoverController.requestSnapshotIfNeeded()
              takeoverController.ensureBridgeBound()
              syncFilterEnabled(takeoverController)
            }
          }
        },
        id = "cebian_input_filter_start",
      )
    }
  }

  private fun registerBridgeReceiver(context: Context) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(receiverContext: Context?, intent: Intent?) {
        val takeoverController = controller
        when (intent?.action) {
          ModuleHookBridgeContract.ACTION_CONFIG_CHANGED -> {
            ensureController()?.onConfigBroadcast(
              intent.getStringExtra(ModuleHookBridgeContract.EXTRA_CONFIG_JSON),
            )
            ensureController()?.let { syncFilterEnabled(it) }
          }
          ModuleHookBridgeContract.ACTION_MODULE_STATUS_REQUEST -> {
            retryInstallIfNeeded()
            // 顺带拉一次配置：app 先于模块启动时会错过变更广播。
            ensureController()?.requestSnapshotIfNeeded()
            sendStatusResponse(context)
          }
          Intent.ACTION_SCREEN_OFF -> {
            takeoverController?.endActiveSession(TakeoverSessionPolicy.REASON_RESET)
          }
          Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
            ensureController()?.requestSnapshotIfNeeded()
            ensureController()?.let { syncFilterEnabled(it) }
          }
        }
      }
    }
    val filter = IntentFilter().apply {
      addAction(ModuleHookBridgeContract.ACTION_CONFIG_CHANGED)
      addAction(ModuleHookBridgeContract.ACTION_MODULE_STATUS_REQUEST)
      addAction(Intent.ACTION_SCREEN_OFF)
      addAction(Intent.ACTION_SCREEN_ON)
      addAction(Intent.ACTION_USER_PRESENT)
    }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
  }

  private fun sendStatusResponse(context: Context, attempt: Int = 0) {
    val detail = buildStatusDetail()
    // 桥接刚发起绑定时状态是 bridge-pending，稍等一下再回，避免 app 误判为未生效。
    if (detail.contains("bridge-pending") && attempt < BRIDGE_WAIT_ATTEMPTS) {
      mainHandler.postDelayed(
        { sendStatusResponse(context, attempt + 1) },
        BRIDGE_WAIT_INTERVAL_MS,
      )
      return
    }
    val active = detail.startsWith("ready")
    runCatching {
      context.sendBroadcast(
        Intent(ModuleHookBridgeContract.ACTION_MODULE_STATUS_RESPONSE).apply {
          setPackage(ModuleHookBridgeContract.MODULE_PACKAGE)
          addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
          putExtra(ModuleHookBridgeContract.EXTRA_STATUS_ACTIVE, active)
          putExtra(ModuleHookBridgeContract.EXTRA_STATUS_DETAIL, detail)
        },
      )
    }.onFailure { XposedLog.w(TAG, "Status response failed: ${it.message}") }
  }

  /**
   * 状态串：`hook=ok,proxy=ok,enable=ok,start=ok,receiver=ok,controller=<detail>[,errors=...]`。
   *
   * `ready` 表示「模块已就绪」：hook/接收器装齐，且已能读到配置快照——**与三个接管开关是否开启无关**。
   * 开关全关时接管本身不生效，但那不是模块的问题；当前接管到底跑没跑由 `controller=` 段表达
   * （`disabled` / `bridge-pending` / `ready:<掩码>`）。
   */
  private fun buildStatusDetail(): String {
    val controllerDetail = controller?.statusDetail() ?: "no-controller"
    val errors = synchronized(moduleErrors) { moduleErrors.toList() }
    val steps = buildString {
      append("hook=").append(if (filterHookInstalled) "ok" else "fail")
      append(",proxy=").append(if (forwardingFilterInstalled) "ok" else "fail")
      append(",enable=").append(if (enableHookInstalled) "ok" else "fail")
      append(",start=").append(if (startHookInstalled) "ok" else "fail")
      append(",receiver=").append(if (receiverRegistered) "ok" else "fail")
      append(",controller=").append(controllerDetail)
      append(",events=").append(eventsSeen.get())
      append(",passed=").append(eventsPassed.get())
      append(",swallowed=").append(eventsSwallowed.get())
      if (errors.isNotEmpty()) {
        append(",errors=").append(errors.joinToString(" | "))
      }
    }
    val ready = filterHookInstalled &&
      enableHookInstalled &&
      startHookInstalled &&
      receiverRegistered &&
      controllerAvailable(controllerDetail)
    return if (ready) "ready:$steps" else "not-ready:$steps"
  }

  /** 控制器是否可用：已读到配置快照（`disabled` 表示开关全关，模块本身是好的）。 */
  private fun controllerAvailable(controllerDetail: String): Boolean =
    controllerDetail.startsWith("ready") ||
      controllerDetail == CONTROLLER_DISABLED ||
      controllerDetail == CONTROLLER_BRIDGE_PENDING

  /** 让 native 层的 InputFilter 开关与当前接管开关保持一致。 */
  private fun syncFilterEnabled(takeoverController: SystemGestureTakeoverController) {
    val enabled = takeoverController.hasEnabledGroups()
    val ims = inputManagerService
    if (ims != null) {
      currentClassLoader?.let { resolveEnableApplier(ims, it) }
    }
    if (enabled) {
      currentClassLoader?.let { ensureForwardingFilter(it) }
    }
    applyFilterEnabled(enabled)
  }

  private fun applyFilterEnabled(enabled: Boolean) {
    if (applyingEnable) return
    applyingEnable = true
    runCatching { enableApplier?.invoke(enabled) }
      .onFailure { XposedLog.w(TAG, "setInputFilterEnabled($enabled) failed: ${it.message}") }
    applyingEnable = false
  }

  /**
   * 安装"原样转发"的 IInputFilter（EdgeX 同款）。
   *
   * 注意：它会让每条输入事件都经由 `IInputFilterHost.sendInputEvent` 回到派发队列，
   * 因此 app 侧在被接管会话期间**不得**再走"注入点击放行"（会形成 app↔模块回环）。
   */
  private fun ensureForwardingFilter(classLoader: ClassLoader) {
    if (forwardingFilterInstalled) return
    val ims = inputManagerService ?: return
    val filterClass = LibXposedReflect.findClassIfExists("android.view.IInputFilter", classLoader)
      ?: run {
        recordModuleError("forwardingFilter: IInputFilter not found")
        return
      }
    val hostClass = LibXposedReflect.findClassIfExists("android.view.IInputFilterHost", classLoader)
      ?: run {
        recordModuleError("forwardingFilter: IInputFilterHost not found")
        return
      }
    val sendInputEvent = runCatching {
      hostClass.getMethod("sendInputEvent", android.view.InputEvent::class.java, Int::class.javaPrimitiveType)
    }.getOrNull() ?: run {
      recordModuleError("forwardingFilter: sendInputEvent not found")
      return
    }

    var hostRef: Any? = null
    val proxy = Proxy.newProxyInstance(classLoader, arrayOf(filterClass), InvocationHandler { _, method, args ->
      when (method.name) {
        "install" -> {
          hostRef = args?.firstOrNull()
          null
        }
        "filterInputEvent" -> {
          val host = hostRef
          val event = args?.firstOrNull() as? android.view.InputEvent
          val policyFlags = (args?.getOrNull(1) as? Int) ?: 0
          if (host != null && event != null) {
            handleFilteredEvent(host, event, policyFlags, sendInputEvent)
          }
          null
        }
        "asBinder" -> android.os.Binder()
        else -> null
      }
    })
    runCatching {
      LibXposedReflect.callMethod(ims, "setInputFilter", proxy)
      forwardingFilterInstalled = true
      XposedLog.i(TAG, "Forwarding input filter installed")
    }.onFailure { recordModuleError(describe("forwardingFilter", it)) }
  }

  /**
   * EdgeX 式丢弃：命中接管区域就**不转发**（事件在输入层消失），其余原样送回管线。
   *
   * 不再依赖 `filterInputEvent` 的返回值——A16 上该方法已经不是可靠的可改返回值路径。
   */
  private fun handleFilteredEvent(
    host: Any,
    event: android.view.InputEvent,
    policyFlags: Int,
    sendInputEvent: java.lang.reflect.Method,
  ) {
    if (forwardingInProgress.get() == true) {
      // 正在转发中再次进入：只放行，避免同一事件被反复转发。
      runCatching { sendInputEvent.invoke(host, event, policyFlags) }
      return
    }
    val motion = event as? MotionEvent
    if (motion != null) {
      eventsSeen.incrementAndGet()
      val takeoverController = controller
      if (takeoverController != null && takeoverController.handleEvent(motion, policyFlags)) {
        eventsSwallowed.incrementAndGet()
        return
      }
      eventsPassed.incrementAndGet()
    }
    forwardingInProgress.set(true)
    try {
      runCatching { sendInputEvent.invoke(host, event, policyFlags) }
    } finally {
      forwardingInProgress.set(false)
    }
  }

  /** 解析“启用/停用 InputFilter”的可用策略；只做能力探测，不改变当前状态。 */
  private fun resolveEnableApplier(ims: Any, classLoader: ClassLoader) {
    if (enableApplier != null) return

    ims.javaClass.declaredMethods
      .firstOrNull { it.name == SET_INPUT_FILTER_ENABLED && it.parameterCount == 1 }
      ?.let { method ->
        method.isAccessible = true
        enableApplier = { value -> method.invoke(ims, value) }
        XposedLog.i(TAG, "InputFilter strategy: InputManagerService.$SET_INPUT_FILTER_ENABLED")
        return
      }

    val native = runCatching { LibXposedReflect.getObjectField(ims, "mNative") }.getOrNull()
    val nativeMethod = native?.javaClass?.declaredMethods
      ?.firstOrNull { it.name == SET_INPUT_FILTER_ENABLED && it.parameterCount == 1 }
    if (native != null && nativeMethod != null) {
      nativeMethod.isAccessible = true
      enableApplier = { value -> nativeMethod.invoke(native, value) }
      XposedLog.i(TAG, "InputFilter strategy: NativeImpl.$SET_INPUT_FILTER_ENABLED")
      return
    }

    val legacyMethod = ims.javaClass.declaredMethods
      .firstOrNull { it.name == NATIVE_SET_INPUT_FILTER_ENABLED && it.parameterCount == 2 }
    if (legacyMethod != null) {
      legacyMethod.isAccessible = true
      val pointer = runCatching { readNativePtr(ims) }.getOrNull()
      if (pointer != null) {
        enableApplier = { value -> legacyMethod.invoke(null, pointer, value) }
        XposedLog.i(TAG, "InputFilter strategy: $NATIVE_SET_INPUT_FILTER_ENABLED")
        return
      }
    }
    recordModuleError("enableStrategy: none (android=${Build.VERSION.SDK_INT})")
  }

  private fun readNativePtr(ims: Any): Long {
    for (field in listOf("mPtr", "mNativePtr")) {
      val value = runCatching { LibXposedReflect.getLongField(ims, field) }.getOrNull()
      if (value != null && value != 0L) return value
    }
    throw NoSuchFieldException("mPtr")
  }

  private fun resolveSystemContext(classLoader: ClassLoader): Context? =
    runCatching {
      val activityThreadClass = LibXposedReflect.findClass("android.app.ActivityThread", classLoader)
      val currentActivityThread =
        LibXposedReflect.callStaticMethod(activityThreadClass, "currentActivityThread")
      LibXposedReflect.callMethod(currentActivityThread!!, "getSystemContext") as Context
    }.getOrNull()

  private companion object {
    const val TAG = "SlideIndexInputFilter"
    const val FILTER_INPUT_EVENT = "filterInputEvent"
    const val SET_INPUT_FILTER_ENABLED = "setInputFilterEnabled"
    const val NATIVE_SET_INPUT_FILTER_ENABLED = "nativeSetInputFilterEnabled"
    const val CONTROLLER_DISABLED = "disabled"
    const val CONTROLLER_BRIDGE_PENDING = "bridge-pending"
    const val RECEIVER_REGISTER_RETRY_MS = 500L
    const val MAX_RECEIVER_REGISTER_ATTEMPTS = 40
    const val BRIDGE_WAIT_ATTEMPTS = 5
    const val BRIDGE_WAIT_INTERVAL_MS = 300L
  }
}
