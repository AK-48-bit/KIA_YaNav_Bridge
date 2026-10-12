plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.myapplication5"
    compileSdk = 34

    defaultConfig {
        // Base applicationId. Each flavor adds its own suffix so that the
        // resulting apps can be installed side by side on the same device.
        applicationId = "com.example.myapplication5"
        minSdk = 29
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One flavor per installable variant. All three share the same code base
    // and only differ in applicationId, app name and launcher icon.
    flavorDimensions += "version"
    productFlavors {
        create("original") {
            dimension = "version"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            resValue("string", "app_name", "KIA Sorento bridge")
        }
        create("kia2") {
            dimension = "version"
            applicationIdSuffix = ".kia2"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_kia2"
            resValue("string", "app_name", "KIA Sorento bridge 2")
        }
        create("kia3") {
            dimension = "version"
            applicationIdSuffix = ".kia3"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher_kia3"
            resValue("string", "app_name", "KIA Sorento bridge 3")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(project(":sdk"))
    implementation(libs.play.services.vision.common)
    implementation(libs.play.services.mlkit.text.recognition.common)
    implementation(libs.play.services.vision)
    implementation(libs.tess.two)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
