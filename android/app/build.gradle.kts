plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.hax.focusdeck"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hax.focusdeck"
        minSdk = 34
        targetSdk = 35
        versionCode = 26
        versionName = "1.22.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("localRelease") {
            val keyPath = providers.environmentVariable("FOCUSDECK_KEYSTORE").orNull
            if (keyPath != null) {
                storeFile = file(keyPath)
                storePassword = providers.environmentVariable("FOCUSDECK_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("FOCUSDECK_KEY_ALIAS").orElse("focusdeck").get()
                keyPassword = providers.environmentVariable("FOCUSDECK_KEY_PASSWORD").orNull
            }
        }
    }
    if(project.hasProperty("uiQaTests")) testBuildType = "uiqa"
    if(project.hasProperty("visualTestTests")) testBuildType = "visualtest"
    buildTypes {
        create("visualtest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".visualtest"
            matchingFallbacks += "debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        create("uiqa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uiqa"
            matchingFallbacks += "debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            if (providers.environmentVariable("FOCUSDECK_KEYSTORE").isPresent) {
                signingConfig = signingConfigs.getByName("localRelease")
            }
        }
    }
    sourceSets.getByName("visualtest") {
        java.srcDir("src/uiqa/java")
        manifest.srcFile("src/uiqa/AndroidManifest.xml")
    }
    sourceSets.getByName("main").assets.srcDir("../../fixtures/developer-mode-3years")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    add("uiqaImplementation", "androidx.compose.ui:ui-test-manifest")
    add("visualtestImplementation", "androidx.compose.ui:ui-test-manifest")
}
