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
        // CI stamps each build with the workflow run number so every APK is a newer version
        // than the last and you can see which build is installed. Local builds stay at 1.
        val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = ciBuild ?: 1
        versionName = if (ciBuild != null) "0.1.$ciBuild" else "0.1.0-local"
    }

    signingConfigs {
        // Checked-in debug key (standard public debug passwords, not a secret) so every
        // machine and CI run signs debug APKs the same way. Without it each CI runner makes
        // its own random key and a new APK won't install over an older one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
