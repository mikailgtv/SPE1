plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val guardianBaseUrl: String = (project.findProperty("guardianBaseUrl") as String?)
    ?: "http://10.0.2.2:8080"
val guardianVersionCode: Int = (project.findProperty("guardianVersionCode") as String?)?.toIntOrNull() ?: 2
val guardianVersionName: String = (project.findProperty("guardianVersionName") as String?)?.trim().orEmpty().ifBlank { "0.1.0-rc.1" }
val guardianReleaseBaseUrl: String = (project.findProperty("guardianReleaseBaseUrl") as String?)?.trim().orEmpty()
val releaseKeystorePath = System.getenv("GL_RELEASE_KEYSTORE")
val releaseStorePassword = System.getenv("GL_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("GL_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("GL_RELEASE_KEY_PASSWORD")

android {
    namespace = "ir.guardianlink.child"
    compileSdk = 36

    defaultConfig {
        applicationId = "ir.guardianlink.child"
        minSdk = 26
        targetSdk = 36
        versionCode = guardianVersionCode
        versionName = guardianVersionName
        buildConfigField("String", "BASE_URL", "\"$guardianBaseUrl\"")
    }

    signingConfigs {
        create("guardianRelease") {
            if (!releaseKeystorePath.isNullOrBlank()) storeFile = file(releaseKeystorePath)
            storePassword = releaseStorePassword.orEmpty()
            keyAlias = releaseKeyAlias.orEmpty()
            keyPassword = releaseKeyPassword.orEmpty()
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$guardianBaseUrl\"")
        }
        release {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("guardianRelease")
            // Release URL is supplied by the operator; no reserved example host is embedded.
            buildConfigField("String", "BASE_URL", "\"$guardianReleaseBaseUrl\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

tasks.configureEach {
    if ((name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package")) && name.contains("Release")) {
        doFirst {
            val host = guardianReleaseBaseUrl.removePrefix("https://").substringBefore('/').lowercase()
            check(host.isNotBlank() && guardianReleaseBaseUrl.startsWith("https://") && !host.endsWith(".example") && host != "localhost") {
                "Set -PguardianReleaseBaseUrl=https://<real-production-host>/v1 before building a release variant"
            }
            check(guardianVersionCode > 1 && Regex("""^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$""").matches(guardianVersionName)) {
                "Set monotonically increasing -PguardianVersionCode and SemVer -PguardianVersionName"
            }
            check(!releaseKeystorePath.isNullOrBlank() && project.file(releaseKeystorePath).isFile && !releaseStorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
                "Provide release signing variables GL_RELEASE_KEYSTORE/GL_RELEASE_STORE_PASSWORD/GL_RELEASE_KEY_ALIAS/GL_RELEASE_KEY_PASSWORD outside the repository"
            }
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:pairing"))
    implementation(project(":feature:childstatus"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
}
