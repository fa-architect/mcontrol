plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.lscontrol"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.lscontrol"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "3.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// 外部ライブラリ依存なし（Android標準APIのみ）
