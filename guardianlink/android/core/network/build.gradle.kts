plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ir.guardianlink.core.network"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:common"))
    api(project(":core:storage"))
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
}
