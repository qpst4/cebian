plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.autofill"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.core.ktx)
}
