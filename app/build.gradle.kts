import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val baseVersionName = "0.1.0"
val versionCodeBase = 1
val resolvedVersionCode: Int = run {
    System.getenv("INKFRAME_VERSION_CODE")?.toIntOrNull()?.let { return@run it }
    val ci = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    if (ci != null) versionCodeBase + ci else versionCodeBase
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("storeFile", "INKFRAME_KEYSTORE")
val hasReleaseSigning = releaseStorePath != null

android {
    namespace = "com.inkframe.studio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.inkframe.studio"
        minSdk = 26
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = baseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = signingValue("storePassword", "INKFRAME_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "INKFRAME_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "INKFRAME_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-model"))
    implementation(project(":engine-gl"))
    implementation(project(":feature-canvas"))
    implementation(project(":feature-timeline"))
    implementation(project(":feature-layers"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
