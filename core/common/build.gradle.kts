plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.common"
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
    implementation(libs.tinypinyin)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
