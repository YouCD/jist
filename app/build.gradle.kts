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
            resources.srcDirs("src/xposed/resources")
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
    
    // MCP Server (external agent integration).
    // The published SDK modules list Ktor server transports and a newer
    // kotlin-stdlib as runtime deps; we only use the pure-protocol classes
    // (Server / ServerSession / ToolRegistry) + our own NanoHTTPD transport,
    // so exclude Ktor and stdlib to keep the APK lean and stay on the
    // project's stdlib (the AGP-pinned Kotlin compiler can't read 2.4 metadata).
    implementation(libs.mcp.kotlin.sdk.core) {
        exclude(group = "io.ktor")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
    implementation(libs.mcp.kotlin.sdk.server) {
        exclude(group = "io.ktor")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
    implementation(libs.nanohttpd)
    // kotlin-logging (transitive of MCP SDK) routes through slf4j, but its
    // Android variant POM drops the slf4j-api dep → add explicitly (NOP logger, no binding)
    implementation(libs.slf4j.api)
    
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
    implementation("io.noties.markwon:ext-tables:4.6.2")
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    // Xposed API (libxposed, only for xposed flavor)
    "xposedCompileOnly"("io.github.libxposed:api:101.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}