import java.net.URLEncoder

// Mongo settings come from the project-root .env (never hard-code credentials in source).
val envValues: Map<String, String> = providers.fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText.orElse("").get()
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
    .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }

fun env(key: String): String = envValues[key] ?: error("Missing $key in .env")
fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.jacaccounts"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.jacaccounts"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val mongoUri = "mongodb://${enc(env("MONGOUSER"))}:${enc(env("MONGOPASSWORD"))}@" +
            "${env("MONGOHOST")}:${env("MONGOPORT")}/?authSource=${enc(env("MONGOAUTH"))}"
        buildConfigField("String", "MONGO_URI", "\"$mongoUri\"")
        buildConfigField("String", "MONGO_DB", "\"jac_accounts\"")
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
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/*.kotlin_module", "META-INF/native-image/**", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.mongodb.driver.kotlin.sync)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}