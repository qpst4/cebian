plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.slideindex.app.overlay.layout"
    compileSdk = 37

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


}

dependencies {
    implementation(project(":core:common"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
