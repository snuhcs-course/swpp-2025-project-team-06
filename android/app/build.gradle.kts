plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.momentag"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.momentag"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.coil.compose)
    implementation(libs.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.material3)
    implementation(libs.material)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.foundation.layout)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.work)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

hilt {
    enableAggregatingTask = false
}

tasks.withType<Test> {
    jvmArgs(
        "-noverify",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
    )
}
