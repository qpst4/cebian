# SlideIndex — R8 / ProGuard rules

# ---------------------------------------------------------------------------
# Debugging (keep line numbers in crash reports)
# ---------------------------------------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions, *Annotation*

# ---------------------------------------------------------------------------
# Xposed / LSPosed module entry (LibXposed API 101 + legacy hook bridge)
# ---------------------------------------------------------------------------
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep class com.slideindex.app.xposed.** { *; }
-keep class com.slideindex.app.autofill.** { *; }

-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**

# ---------------------------------------------------------------------------
# Kotlin coroutines
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# Jetpack Compose (compiler emits rules; keep runtime essentials)
# ---------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# DataStore Preferences (protobuf lite)
# ---------------------------------------------------------------------------
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-keepclassmembers class androidx.datastore.preferences.** {
    <fields>;
}
-dontwarn androidx.datastore.**

# ---------------------------------------------------------------------------
# Shizuku
# ---------------------------------------------------------------------------
-keep class rikka.shizuku.** { *; }
-keep class dev.rikka.shizuku.** { *; }
-keep interface com.slideindex.app.shizuku.ITaskManagerService { *; }
-keep class com.slideindex.app.shizuku.** { *; }

# ---------------------------------------------------------------------------
# AIDL / Binder stubs
# ---------------------------------------------------------------------------
-keep class * implements android.os.IInterface { *; }
-keepclassmembers class * implements android.os.IInterface {
    public static ** CREATOR;
}

# ---------------------------------------------------------------------------
# Third-party libraries
# ---------------------------------------------------------------------------
-keep class com.github.skydoves.colorpicker.** { *; }
-keep class net.sourceforge.pinyin4j.** { *; }
-dontwarn net.sourceforge.pinyin4j.**

# ---------------------------------------------------------------------------
# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.* <fields>;
}
# ---------------------------------------------------------------------------
-keep class com.slideindex.app.SlideIndexApp { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.content.ContentProvider { *; }

# ---------------------------------------------------------------------------
# Android framework types used by codecs & intents
# ---------------------------------------------------------------------------
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers enum com.slideindex.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
