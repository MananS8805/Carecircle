plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

fun secret(prop: String, env: String): String? = (project.findProperty(prop) as String?) ?: System.getenv(env)

android {
    namespace = "com.carecircle"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carecircle"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Deployed Vercel URL of /backend. Override in local.properties: careCircleApi=https://your-app.vercel.app/
        val api = (project.findProperty("careCircleApi") as String?) ?: "https://carecircle-api.example.vercel.app/"
        buildConfigField("String", "API_BASE_URL", "\"$api\"")
        buildConfigField("String", "APP_KEY", "\"${(project.findProperty("careCircleAppKey") as String?) ?: ""}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    // Release signing: reads the keystore from gradle properties or environment variables. Without them the release
    // build is simply unsigned, so debug builds and CI never need secrets.
    val keystorePath = secret("careCircleKeystore", "CARECIRCLE_KEYSTORE")
    val canSign = keystorePath != null && file(keystorePath).exists()
    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = secret("careCircleKeystorePassword", "CARECIRCLE_KEYSTORE_PASSWORD")
                keyAlias = secret("careCircleKeyAlias", "CARECIRCLE_KEY_ALIAS")
                keyPassword = secret("careCircleKeyPassword", "CARECIRCLE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        abortOnError = true
        error += setOf("MissingTranslation", "HardcodedText")
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.google.mlkit:text-recognition:16.0.1")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
