import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.slideindex.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.slideindex.app"
        minSdk = 31
        targetSdk = 37
        versionCode = 16
        versionName = "1.6.20"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    ndkVersion = "28.2.13676358"

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    androidResources {
        ignoreAssetsPattern += "dict:.*"
    }

    sourceSets {
        named("main") {
            assets.srcDir("build/generated/release-assets")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            merges += "META-INF/xposed/*"
        }
        jniLibs {
            excludes += setOf(
                "**/libopencv_java4.so",
                "**/libonnxruntime.so",
                "**/libtesseract.so",
                "**/libleptonica.so",
                "**/libtranslate_jni.so",
                "**/liblanguage_id_l2c_jni.so",
                "**/libslideindex_jieba.so",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

private val NATIVE_ENGINE_ABI = "arm64-v8a"

private data class NativeEnginePackSpec(
    val taskName: String,
    val zipName: String,
    val libraries: List<String>,
    val assetPaths: List<String> = emptyList(),
)

private val nativeEnginePackSpecs = listOf(
    NativeEnginePackSpec(
        taskName = "packageOcrEnginePack",
        zipName = "ocr-engine-arm64-v1.zip",
        libraries = listOf(
            "libonnxruntime.so",
            "libopencv_java4.so",
            "libleptonica.so",
            "libtesseract.so",
        ),
    ),
    NativeEnginePackSpec(
        taskName = "packageTranslateEnginePack",
        zipName = "translate-engine-arm64-v1.zip",
        libraries = listOf(
            "libtranslate_jni.so",
            "liblanguage_id_l2c_jni.so",
        ),
    ),
    NativeEnginePackSpec(
        taskName = "packageSegmentationEnginePack",
        zipName = "segmentation-engine-arm64-v1.zip",
        libraries = listOf("libslideindex_jieba.so"),
        assetPaths = listOf(
            "dict/jieba.dict.utf8",
            "dict/hmm_model.utf8",
            "dict/user.dict.utf8",
        ),
    ),
)

val nativeEnginePacksDir = rootProject.layout.buildDirectory.dir("native-engine-packs")
val nativeEnginePackLibDir = layout.buildDirectory.dir("native-engine-pack-libs/$NATIVE_ENGINE_ABI")

val nativeEnginePackArtifacts = configurations.create("nativeEnginePackArtifacts") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

fun extractArm64LibsFromAar(aar: File, destination: File) {
    zipTree(aar).matching { include("jni/$NATIVE_ENGINE_ABI/*.so") }.forEach { entry ->
        entry.copyTo(File(destination, entry.name), overwrite = true)
    }
}

tasks.register("collectNativeEnginePackLibs") {
    group = "build"
    description = "Extract arm64 native libraries from dependency AARs for engine packs."
    notCompatibleWithConfigurationCache("Extracts JNI libraries from resolved AAR artifacts")
    dependsOn("buildCMakeRelWithDebInfo[$NATIVE_ENGINE_ABI]")
    inputs.files(nativeEnginePackArtifacts)
    outputs.dir(nativeEnginePackLibDir)
    doLast {
        val destination = nativeEnginePackLibDir.get().asFile.apply { mkdirs() }
        destination.listFiles()?.forEach { it.delete() }
        nativeEnginePackArtifacts.files.forEach { artifact ->
            if (artifact.extension.equals("aar", ignoreCase = true)) {
                extractArm64LibsFromAar(artifact, destination)
            }
        }
        val jieba = fileTree(layout.buildDirectory.dir("intermediates/cxx")) {
            include("**/RelWithDebInfo/**/obj/$NATIVE_ENGINE_ABI/libslideindex_jieba.so")
        }.files.maxByOrNull { it.lastModified() }
            ?: throw GradleException("libslideindex_jieba.so not found; run CMake release build first")
        jieba.copyTo(File(destination, "libslideindex_jieba.so"), overwrite = true)
    }
}

val packTasks = nativeEnginePackSpecs.map { spec ->
    tasks.register<Zip>(spec.taskName) {
        group = "build"
        description = "Package ${spec.zipName} for bundled offline install."
        dependsOn("collectNativeEnginePackLibs")
        archiveFileName.set(spec.zipName)
        destinationDirectory.set(nativeEnginePacksDir)
        from(nativeEnginePackLibDir) {
            include(spec.libraries)
            into("lib/$NATIVE_ENGINE_ABI")
        }
        spec.assetPaths.forEach { assetPath ->
            val assetFile = file("src/main/assets/$assetPath")
            from(assetFile.parentFile) {
                include(assetFile.name)
                into("assets/${assetFile.parentFile.relativeTo(file("src/main/assets"))}")
            }
        }
    }
}

tasks.register("packageNativeEnginePacks") {
    group = "build"
    description = "Package all native engine zips for release APK bundling."
    dependsOn(packTasks)
}

val bundledNativeEngineGeneratedAssets = layout.buildDirectory.dir("generated/release-assets")

tasks.register<Copy>("copyBundledNativeEnginePacks") {
    group = "build"
    description = "Copy native engine zip packs into generated app assets for offline-first install."
    val sourceDir = rootProject.layout.buildDirectory.dir("native-engine-packs")
    from(sourceDir) {
        include("*-arm64-v1.zip")
        eachFile {
            val packId = name.removeSuffix("-arm64-v1.zip")
            path = "bundled-native-engine/$packId.zip"
        }
        includeEmptyDirs = false
    }
    into(bundledNativeEngineGeneratedAssets)
    outputs.dir(bundledNativeEngineGeneratedAssets)
}

tasks.named("copyBundledNativeEnginePacks") {
    dependsOn("packageNativeEnginePacks")
}

afterEvaluate {
    tasks.named("mergeReleaseAssets") {
        dependsOn("copyBundledNativeEnginePacks")
    }
    tasks.matching {
        it.name.startsWith("generateReleaseLint") || it.name == "lintVitalAnalyzeRelease"
    }.configureEach {
        dependsOn("copyBundledNativeEnginePacks")
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:autofill"))
    implementation(project(":core:gesture"))
    implementation(project(":core:notification"))
    implementation(project(":core:monitoring"))
    implementation(project(":core:overlay-layout"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:otp"))
    implementation(project(":feature:notification"))
    implementation(project(":feature:apps"))
    implementation(project(":feature:shake"))
    implementation(project(":feature:message"))
    implementation(project(":core:ocr"))
    implementation(project(":core:translate"))
    implementation(project(":core:native-engine"))

    nativeEnginePackArtifacts(libs.onnxruntime.android)
    nativeEnginePackArtifacts(libs.opencv.android)
    nativeEnginePackArtifacts(libs.tesseract4android)
    nativeEnginePackArtifacts(libs.mlkit.translate)
    nativeEnginePackArtifacts(libs.mlkit.language.id)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    implementation(libs.core.ktx)
    implementation(libs.profileinstaller)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.savedstate)
    implementation(libs.activity.compose)
    implementation("androidx.webkit:webkit:1.12.1")
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(libs.datastore.preferences)
    implementation(libs.tinypinyin)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(platform(libs.compose.bom.alpha))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.colorpicker.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.haze)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
