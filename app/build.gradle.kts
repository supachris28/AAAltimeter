plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.supachris28.aaaltimeter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.supachris28.aaaltimeter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.car.app)
    // Lets the app run on Android Auto (phone projected to the car screen).
    implementation(libs.androidx.car.app.projected)
    implementation(libs.androidx.lifecycle.common)

    testImplementation(libs.junit)
}
