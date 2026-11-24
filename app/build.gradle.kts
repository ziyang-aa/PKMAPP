import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.example.pkmapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.pkmapp"
        if (providers.gradleProperty("studyTestIsolation").orNull == "true") {
            applicationId = "com.example.pkmapp.studytest"
            testApplicationId = "com.example.pkmapp.studytest.test"
        }
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseSigningProps = Properties().apply {
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.isFile) {
            localPropsFile.inputStream().use { load(it) }
        }
    }
    fun signingProp(propKey: String, envKey: String): String? =
        releaseSigningProps.getProperty(propKey)?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getenv(envKey)?.trim()?.takeIf { it.isNotEmpty() }

    val storeFilePath = signingProp("release.storeFile", "PKM_STORE_FILE")
    val storePasswordValue = signingProp("release.storePassword", "PKM_STORE_PASSWORD")
    val keyAliasValue = signingProp("release.keyAlias", "PKM_KEY_ALIAS")
    val keyPasswordValue = signingProp("release.keyPassword", "PKM_KEY_PASSWORD")
    val resolvedKeystore = storeFilePath?.let { path ->
        rootProject.file(path).takeIf { it.isFile } ?: file(path).takeIf { it.isFile }
    }
    val hasReleaseSigning = resolvedKeystore != null &&
        storePasswordValue != null &&
        keyAliasValue != null &&
        keyPasswordValue != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = resolvedKeystore
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else if (providers.gradleProperty("useDebugSigningForRelease").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.fragment)
    implementation(libs.recyclerview)
    implementation(libs.fragment.ktx)
    implementation(libs.activity.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.rules)
}
