plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.wire)
}

android {
    namespace = "com.nago8.chat.old"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nago8.chat.old"
        minSdk = 16
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.3-snapshot-26w17a"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.androidsvg)
    implementation(libs.appcompat)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.wire.runtime)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.glide)
    implementation(libs.touch.image.view)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

wire {
    java {}
}
