plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

val gitTag: String = try {
    val process = Runtime.getRuntime().exec(arrayOf("git", "describe", "--tags", "--abbrev=0"))
    if (process.waitFor() != 0) throw Exception("git describe failed")
    process.inputStream.bufferedReader().readText().trim().removePrefix("v")
} catch (_: Exception) { "0.0.1" }
val versionParts = gitTag.split(".").map { it.toIntOrNull() ?: 0 }
val generatedVersionCode = when {
    versionParts.size >= 3 -> versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
    versionParts.size == 2 -> versionParts[0] * 10000 + versionParts[1] * 100
    else -> versionParts[0] * 10000
}

android {
    namespace = "dev.rcht.jist"
    compileSdk = 36

    flavorDimensions += "channel"
    productFlavors {
        create("normal") {
            dimension = "channel"
            applicationId = "dev.rcht.jist"
            versionNameSuffix = ""
            buildConfigField("boolean", "isXposedFlavor", "false")
        }
        create("xposed") {
            dimension = "channel"
            applicationId = "dev.rcht.jist"
            versionNameSuffix = "-xposed"
            buildConfigField("boolean", "isXposedFlavor", "true")
        }
    }

    defaultConfig {
        applicationId = "dev.rcht.jist"
        minSdk = 29
        targetSdk = 36
        versionCode = generatedVersionCode
        versionName = gitTag

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("xposed") {
            java.srcDirs("src/xposed/java")
            kotlin.srcDirs("src/xposed/java")
            assets.srcDirs("src/xposed/assets")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    lint {
        disable.add("ProtectedPermissions")
        disable.add("Instantiatable")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = false
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)
    
    // OkHttp & Networking
    implementation(libs.okhttp)
    
    // JSON
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.compose.foundation:foundation-layout:1.10.0")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation("io.noties.markwon:core:4.6.2")
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Xposed API (local jar, only for xposed flavor)
    "xposedCompileOnly"(files("libs/api-82.jar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}