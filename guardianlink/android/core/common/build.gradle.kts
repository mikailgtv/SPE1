plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ir.guardianlink.core.common"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
}
