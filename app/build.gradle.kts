import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stableUpdatePropertiesFile = rootProject.file("signing/keystore.properties")
val stableUpdateProperties = Properties().apply {
    if (stableUpdatePropertiesFile.isFile) {
        stableUpdatePropertiesFile.inputStream().use(::load)
    }
}
val stableUpdateStoreFile = stableUpdateProperties.getProperty("storeFile")
    ?.takeIf(String::isNotBlank)
    ?.let(rootProject::file)
val hasStableUpdateSigning = stableUpdateStoreFile?.isFile == true &&
    listOf("storePassword", "keyAlias", "keyPassword").all {
        stableUpdateProperties.getProperty(it).orEmpty().isNotBlank()
    }

android {
    namespace = "com.example.englishcantoneselearning"
    buildToolsVersion = "36.1.0"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.englishcantoneselearning"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    signingConfigs {
        if (hasStableUpdateSigning) {
            create("stableUpdate") {
                storeFile = stableUpdateStoreFile
                storePassword = stableUpdateProperties.getProperty("storePassword")
                keyAlias = stableUpdateProperties.getProperty("keyAlias")
                keyPassword = stableUpdateProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("stableUpdate")?.let { signingConfig = it }
        }
        create("uiTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            versionNameSuffix = "-uitest"
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("stableUpdate")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Instrumented tests install and remove their target package. Keep them on an isolated
    // application ID so a connected test run can never erase the user's keys or materials.
    testBuildType = "uiTest"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val verifyStableUpdateSigning by tasks.registering {
    group = "verification"
    description = "Fails when the private stable-update signing material is unavailable."
    doLast {
        check(hasStableUpdateSigning) {
            "Stable update signing is not configured. Copy signing/keystore.properties.example " +
                "to signing/keystore.properties and point it at the original update keystore."
        }
    }
}

tasks.matching {
    it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "signReleaseBundle")
}.configureEach {
    dependsOn(verifyStableUpdateSigning)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20250517")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    "uiTestImplementation"("androidx.compose.ui:ui-tooling")
    "uiTestImplementation"("androidx.compose.ui:ui-test-manifest")
}
