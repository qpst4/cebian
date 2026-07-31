package com.slideindex.app.util

import android.os.Build
import android.os.Bundle
import android.os.Parcelable

object BundleParcelCompat {
    fun getValue(bundle: Bundle, key: String): Any? {
        @Suppress("DEPRECATION")
        return bundle.get(key)
    }

    fun getParcelableArray(bundle: Bundle, key: String): Array<out Parcelable>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelableArray(key, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelableArray(key)
        }
    }

    fun getParcelableArrayOfBundles(bundle: Bundle, key: String): Array<Bundle>? {
        val array: Array<out Parcelable>? = runCatching {
            getParcelableArray(bundle, key)
        }.getOrElse {
            @Suppress("DEPRECATION")
            bundle.getParcelableArray(key)
        }
        return array
            ?.mapNotNull { it as? Bundle }
            ?.toTypedArray()
    }

    fun <T : Parcelable> getParcelable(bundle: Bundle, key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            val parcelable = bundle.getParcelable<Parcelable>(key)
            if (clazz.isInstance(parcelable)) clazz.cast(parcelable) else null
        }
    }
}
