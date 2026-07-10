package com.slideindex.app.xposed

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

internal object LibXposedReflect {
  fun findClass(name: String, classLoader: ClassLoader): Class<*> =
    Class.forName(name, false, classLoader)

  fun findClassIfExists(name: String, classLoader: ClassLoader): Class<*>? =
    runCatching { findClass(name, classLoader) }.getOrNull()

  fun findMethodExactIfExists(
    clazz: Class<*>,
    name: String,
    vararg parameterTypes: Class<*>?,
  ): Method? {
    val types = parameterTypes.filterNotNull().toTypedArray()
    if (types.size != parameterTypes.size) return null
    return runCatching {
      clazz.getDeclaredMethod(name, *types).apply { isAccessible = true }
    }.getOrNull()
      ?: runCatching {
        clazz.getMethod(name, *types).apply { isAccessible = true }
      }.getOrNull()
  }

  fun getObjectField(obj: Any, fieldName: String): Any? {
    var clazz: Class<*>? = obj.javaClass
    while (clazz != null) {
      runCatching {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(obj)
      }
      clazz = clazz.superclass
    }
    throw NoSuchFieldException(fieldName)
  }

  fun getIntField(obj: Any, fieldName: String): Int {
    var clazz: Class<*>? = obj.javaClass
    while (clazz != null) {
      runCatching {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.getInt(obj)
      }
      clazz = clazz.superclass
    }
    throw NoSuchFieldException(fieldName)
  }

  fun getStaticIntField(clazz: Class<*>, fieldName: String): Int {
    val field = findField(clazz, fieldName)
    return field.getInt(null)
  }

  fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any? {
    if (obj == null) return null
    val types = args.map { arg ->
      when (arg) {
        null -> Any::class.java
        is Boolean -> Boolean::class.javaPrimitiveType!!
        is Int -> Int::class.javaPrimitiveType!!
        is Long -> Long::class.javaPrimitiveType!!
        is Float -> Float::class.javaPrimitiveType!!
        is Double -> Double::class.javaPrimitiveType!!
        is Byte -> Byte::class.javaPrimitiveType!!
        is Short -> Short::class.javaPrimitiveType!!
        is Char -> Char::class.javaPrimitiveType!!
        else -> arg.javaClass
      }
    }.toList().toTypedArray<Class<*>>()
    val method = findMethod(obj.javaClass, methodName, types, args)
    return method.invoke(obj, *args)
  }

  fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
    val types = args.map { arg ->
      when (arg) {
        null -> Any::class.java
        is Boolean -> Boolean::class.javaPrimitiveType!!
        is Int -> Int::class.javaPrimitiveType!!
        is Long -> Long::class.javaPrimitiveType!!
        is Float -> Float::class.javaPrimitiveType!!
        is Double -> Double::class.javaPrimitiveType!!
        is Byte -> Byte::class.javaPrimitiveType!!
        is Short -> Short::class.javaPrimitiveType!!
        is Char -> Char::class.javaPrimitiveType!!
        else -> arg.javaClass
      }
    }.toList().toTypedArray<Class<*>>()
    val method = findMethod(clazz, methodName, types, args, static = true)
    return method.invoke(null, *args)
  }

  private fun findField(clazz: Class<*>, fieldName: String): Field {
    var current: Class<*>? = clazz
    while (current != null) {
      runCatching {
        return current.getDeclaredField(fieldName).apply { isAccessible = true }
      }
      current = current.superclass
    }
    throw NoSuchFieldException(fieldName)
  }

  private fun findMethod(
    clazz: Class<*>,
    methodName: String,
    parameterTypes: Array<Class<*>>,
    args: Array<out Any?>,
    static: Boolean = false,
  ): Method {
    val candidates = if (static) clazz.methods + clazz.declaredMethods else clazz.declaredMethods + clazz.methods
    for (method in candidates.distinctBy { "${it.name}:${it.parameterTypes.joinToString()}" }) {
      if (method.name != methodName) continue
      if (method.parameterTypes.size != args.size) continue
      if (matchesParameters(method.parameterTypes, args)) {
        method.isAccessible = true
        return method
      }
    }
    throw NoSuchMethodException("$methodName(${parameterTypes.joinToString()})")
  }

  private fun matchesParameters(parameterTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
  if (parameterTypes.size != args.size) return false
    for (index in parameterTypes.indices) {
      val expected = parameterTypes[index]
      val value = args[index]
      if (value == null) continue
      if (!wrap(expected).isAssignableFrom(wrap(value.javaClass))) return false
    }
    return true
  }

  private fun wrap(type: Class<*>): Class<*> = when (type) {
    java.lang.Boolean.TYPE -> Boolean::class.java
    java.lang.Integer.TYPE -> Int::class.java
    java.lang.Long.TYPE -> Long::class.java
    java.lang.Float.TYPE -> Float::class.java
    java.lang.Double.TYPE -> Double::class.java
    java.lang.Byte.TYPE -> Byte::class.java
    java.lang.Short.TYPE -> Short::class.java
    java.lang.Character.TYPE -> Char::class.java
    else -> type
  }

  fun findConstructorExactIfExists(clazz: Class<*>, vararg parameterTypes: Class<*>): Constructor<*>? =
    runCatching {
      clazz.getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }
    }.getOrNull()
}
