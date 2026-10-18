plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.prav1525.munch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prav1525.munch"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

import java.net.URL

val downloadHeyJarvisWakeModel = tasks.register("downloadHeyJarvisWakeModel") {
    val modelFile = layout.buildDirectory.file("generated/wakeword-assets/hey_jarvis_v0.1.onnx")
    outputs.file(modelFile)
    doLast {
        val destination = modelFile.get().asFile
        if (!destination.exists()) {
            destination.parentFile.mkdirs()
            URL("https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/hey_jarvis_v0.1.onnx")
                .openStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
        }
        check(destination.length() > 100_000L) {
            "Hey Jarvis ONNX model download is missing or incomplete: $destination"
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(
    layout.buildDirectory.dir("generated/wakeword-assets")
)
tasks.named("preBuild").configure {
    dependsOn(downloadHeyJarvisWakeModel)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media:media:1.7.0")
    implementation("com.github.suxsem:AndroidWakeWord:v0.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
