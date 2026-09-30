plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.lscontrol"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.lscontrol"
        minSdk = 33
        targetSdk = 34
        versionCode = 5
        versionName = "3.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
// 外部ライブラリ依存なし（Android標準APIのみ）

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":llama"))
}
