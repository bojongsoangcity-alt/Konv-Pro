import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Compose Compiler plugin TETAP wajib sejak Kotlin 2.0 (berdiri sendiri tanpa
    // plugin kotlin.android yang sudah tidak dipakai sejak AGP 9).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * ---------------------------------------------------------------------------
 * KONFIGURASI AI - DEVELOPER MANAGED (Stage 2)
 * ---------------------------------------------------------------------------
 * Kredensial AI TIDAK ditulis di source code. Nilainya dibaca saat build dari:
 *   1) local.properties  (tidak masuk version control - lihat .gitignore)
 *   2) Gradle project property / CI secret  (-Pai.provider.gemini.key=...)
 *   3) variabel environment CI (dipetakan oleh pipeline ke -P...)
 * Hasil akhirnya ditempel ke BuildConfig.AI_CONFIG sebagai JSON flat.
 *
 * Pengguna aplikasi TIDAK dapat melihat atau mengubah nilai ini.
 * Catatan keamanan: BuildConfig dapat diekstrak dari APK. Lihat
 * docs/AI_SECURITY.md - obfuscation bukan perlindungan sempurna.
 * ---------------------------------------------------------------------------
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun aiConfigValue(key: String): String? =
    (localProperties.getProperty(key) ?: (project.findProperty(key) as String?))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

val aiFixedKeys = listOf(
    "ai.externalEnabled",
    "ai.primary",
    "ai.fallback",
    "ai.limits.maxRequestsPerDay",
    "ai.limits.maxTokensPerDay",
    "ai.limits.maxCostUsdPerDay",
    "ai.limits.globalTimeoutMs",
    "ai.limits.maxFallbackAttempts",
    "ai.privacy.requireConsent"
)

val aiProviderKeys = buildSet {
    localProperties.stringPropertyNames().filter { it.startsWith("ai.provider.") }.forEach { add(it) }
    project.properties.keys.filter { it.startsWith("ai.provider.") }.forEach { add(it) }
}.sorted()

val aiConfigMap: Map<String, String> =
    (aiFixedKeys + aiProviderKeys)
        .mapNotNull { key -> aiConfigValue(key)?.let { key to it } }
        .toMap()

/** JSON flat object; nilai di-escape agar aman masuk string BuildConfig. */
val aiConfigJson: String = aiConfigMap.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) ->
    val safe = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
    "\"$k\":\"$safe\""
}

android {
    namespace = "com.dooyappwork.konveksipro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dooyappwork.konveksipro"
        minSdk = 24          // Android 7.0 - keputusan final proyek
        targetSdk = 36       // Siap kebijakan distribusi masa depan
        versionCode = 4
        versionName = "0.6.1-stage7"

        // Konfigurasi AI hasil build (bukan input pengguna).
        buildConfigField("String", "AI_CONFIG", "\"$aiConfigJson\"")
        // Halaman diagnostik developer hanya ikut pada build yang mengaktifkannya.
        buildConfigField(
            "boolean",
            "DEV_DIAGNOSTICS",
            (aiConfigValue("dev.diagnostics")?.toBooleanStrictOrNull() ?: false).toString()
        )
    }

    buildTypes {
        debug {
            // Diagnostik tetap butuh flag eksplisit; tidak otomatis aktif.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
