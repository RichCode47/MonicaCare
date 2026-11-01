import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Only public client configuration is compiled into the APK. Never read secret key names.
val supabaseProperties = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
fun publicConfig(name: String): String =
    providers.gradleProperty(name).orNull ?: supabaseProperties.getProperty(name)
    ?: providers.environmentVariable(name).orNull.orEmpty()
fun configLiteral(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""
val isolatedQa = providers.gradleProperty("monicacareIsolatedQa").orNull == "true"
val allowQaCloud = providers.gradleProperty("monicacareQaCloud").orNull == "true"
val supabaseUrl = if (isolatedQa && !allowQaCloud) "" else publicConfig("SUPABASE_URL")
val supabasePublishableKey = if (isolatedQa && !allowQaCloud) "" else publicConfig("SUPABASE_PUBLISHABLE_KEY")
require(supabasePublishableKey.isBlank() || supabasePublishableKey.startsWith("sb_publishable_")) {
    "SUPABASE_PUBLISHABLE_KEY must be a public sb_publishable_ key. Secret/service-role/legacy keys are rejected."
}

android {
    namespace = "com.monicacare.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.monicacare.app"
        buildConfigField("String", "SUPABASE_URL", configLiteral(supabaseUrl))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", configLiteral(supabasePublishableKey))
        // Explicit opt-in for emulator QA alongside the user's installed app and records.
        if (isolatedQa) {
            applicationIdSuffix = ".qa"
        }
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation("io.ktor:ktor-client-mock:3.3.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
