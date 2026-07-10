package com.slideindex.app.xposed

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

internal class HookParam(
  private val chain: XposedInterface.Chain,
  initialResult: Any? = null,
) {
  val thisObject: Any? get() = chain.thisObject
  val args: Array<Any?> get() = chain.args.toTypedArray()
  var result: Any? = initialResult
  var throwable: Throwable? = null
  var returnEarly: Boolean = false

  fun arg(index: Int): Any? = chain.getArg(index)
}

internal abstract class LibXposedMethodHook : XposedInterface.Hooker {
  open fun beforeHookedMethod(param: HookParam) {}

  open fun afterHookedMethod(param: HookParam) {}

  final override fun intercept(chain: XposedInterface.Chain): Any? {
    val param = HookParam(chain)
    beforeHookedMethod(param)
    param.throwable?.let { throw it }
    val result = if (param.returnEarly) {
      param.result
    } else {
      try {
        chain.proceed()
      } catch (throwable: Throwable) {
        param.throwable = throwable
        throw throwable
      }
    }
    param.result = result
    afterHookedMethod(param)
    param.throwable?.let { throw it }
    return param.result
  }
}

internal fun XposedInterface.hookMethod(
  executable: Executable,
  hooker: XposedInterface.Hooker,
  id: String? = null,
): XposedInterface.HookHandle {
  var builder = hook(executable)
  if (id != null) {
    builder = builder.setId(id)
  }
  return builder.intercept(hooker)
}

internal fun XposedInterface.hookMethod(
  executable: Executable,
  hook: LibXposedMethodHook,
  id: String? = null,
): XposedInterface.HookHandle = hookMethod(executable, hooker = hook, id = id)
