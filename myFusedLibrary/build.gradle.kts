plugins {
    alias(libs.plugins.android.fusedlibrary)
    `maven-publish`
}


androidFusedLibrary {
    namespace = "com.example.myFusedLibrary"
    minSdk {
        version = release(24)
    }
}

dependencies {

    include(project(":StepCounter"))

    include("com.intuit.sdp:sdp-android:1.1.1")

/*    include("androidx.savedstate:savedstate-android:1.4.0")
    include("androidx.savedstate:savedstate:1.4.0")
    include("androidx.savedstate:savedstate-ktx:1.4.0")*/
    include("androidx.core:core-ktx:1.17.0")
    //    include("androidx.customview:customview-poolingcontainer:1.0.0")


    include("com.github.PhilJay:MPAndroidChart:v3.1.0")
    include("androidx.preference:preference:1.2.1")

    include("io.github.ShawnLin013:number-picker:2.4.13")
    include("androidx.room:room-runtime:2.8.4")
    include("androidx.room:room-ktx:2.8.4")


    include("androidx.lifecycle:lifecycle-runtime:2.10.0")
    include("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    include("androidx.lifecycle:lifecycle-common:2.10.0")
    include("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    include("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")

/*
    include("androidx.activity:activity-ktx:1.12.3")
    include("androidx.activity:activity:1.12.3")
    include("androidx.appcompat:appcompat:1.7.1")
    include("androidx.constraintlayout:constraintlayout:2.2.1")
    include("androidx.recyclerview:recyclerview:1.3.1")
    include("androidx.fragment:fragment-ktx:1.8.9")
    include("androidx.fragment:fragment:1.8.9")
    include("com.google.android.gms:play-services-basement:18.10.0")
    include("com.google.android.gms:play-services-tasks:18.4.1")
    include("com.google.android.play:feature-delivery:2.1.0")
    include("androidx.viewpager2:viewpager2:1.1.0")
*/

/*    include("androidx.navigation:navigation-fragment-ktx:2.9.7")
    include("androidx.navigation:navigation-fragment:2.9.7")
    include("androidx.navigation:navigation-dynamic-features-fragment:2.9.7")
    include("androidx.navigation:navigation-ui-ktx:2.9.7")
    include("androidx.navigation:navigation-common-android:2.9.7")
    include("androidx.navigation:navigation-runtime-android:2.9.7")
    include("androidx.navigation:navigation-runtime:2.9.7")
    include("androidx.navigation:navigation-dynamic-features-runtime:2.9.7")
    include("androidx.navigation:navigation-common:2.9.7")
    include("androidx.navigation:navigation-ui:2.9.7")*/

    include("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    include("com.android.tools:desugar_jdk_libs:2.1.5")


    include("com.google.android.material:material:1.13.0")

}

/*
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.library.myfusedlibrary"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}*/
