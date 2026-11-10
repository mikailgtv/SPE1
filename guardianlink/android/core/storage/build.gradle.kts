plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ir.guardianlink.core.storage"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.androidx.datastore)
    implementation(libs.coroutines.core)
    api(project(":core:common"))
}
