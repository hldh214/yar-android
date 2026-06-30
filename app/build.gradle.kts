import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(envName)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("ANDROID_SIGNING_STORE_FILE", "android.signing.storeFile")
val releaseStoreBase64 = signingValue("ANDROID_SIGNING_KEYSTORE_BASE64", "android.signing.keystoreBase64")
val releaseStorePassword = signingValue("ANDROID_SIGNING_STORE_PASSWORD", "android.signing.storePassword")
val releaseKeyAlias = signingValue("ANDROID_SIGNING_KEY_ALIAS", "android.signing.keyAlias")
val releaseKeyPassword = signingValue("ANDROID_SIGNING_KEY_PASSWORD", "android.signing.keyPassword")
val decodedReleaseStoreFile = releaseStoreBase64?.let {
    layout.buildDirectory.file("generated/signing/release.keystore").get().asFile.also { storeFile ->
        storeFile.parentFile.mkdirs()
        storeFile.writeBytes(Base64.getDecoder().decode(it))
    }
}
val resolvedReleaseStoreFile = releaseStoreFile?.let { file(it) } ?: decodedReleaseStoreFile
val hasReleaseSigning = listOf(
    resolvedReleaseStoreFile?.path,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.yar.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.yar.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 10001
        versionName = "1.0.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = resolvedReleaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.guava)
    implementation(libs.kotlinx.coroutines.guava)
    testImplementation(libs.junit)
}
