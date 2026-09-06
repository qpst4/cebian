plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.monitoring"
    compileSdk = 37

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

dependencies {
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
