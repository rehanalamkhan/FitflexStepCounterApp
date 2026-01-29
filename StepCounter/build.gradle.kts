import org.gradle.kotlin.dsl.coreLibraryDesugaring
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("maven-publish")
}

val githubProperties = Properties().apply {
    val file = rootProject.file("github.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

android {
    namespace = "com.step.counter"
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

    buildFeatures {
        buildConfig = true
        viewBinding = false
        dataBinding = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}


kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}


publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "io.github.muhammadfaizansohail"
            artifactId = "stepcounter"
            version = "1.0.1"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("stepcounter")
                description.set("app for step counter")
                url.set("https://github.com/MuhammadFaizanSohail/StepCounterAppSample.git")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("MuhammadFaizanSohail")
                        name.set("Faizan")
                        email.set("your.email@example.com")
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/MuhammadFaizanSohail/StepCounterAppSample.git"
                    developerConnection = "scm:git:ssh://github.com/MuhammadFaizanSohail/StepCounterAppSample.git"
                    url = "http://github.com/MuhammadFaizanSohail/StepCounterAppSample/"
                }
            }
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.17.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")

    implementation("com.google.android.material:material:1.13.0")

    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")

    implementation("com.intuit.sdp:sdp-android:1.1.1")


    // Views/Fragments integration
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.7")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    implementation("androidx.navigation:navigation-dynamic-features-fragment:2.9.7")
    implementation("androidx.activity:activity-ktx:1.12.3")

    androidTestImplementation("androidx.navigation:navigation-testing:2.9.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")


    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.room:room-paging:2.8.4")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("io.github.ShawnLin013:number-picker:2.4.13")

}
