plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.common"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.tinypinyin)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
