plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.slideindex.app.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
