plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    kotlin("plugin.serialization") version "1.8.0"
    signing
}

android {
    compileSdk = 34

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        renderscriptTargetApi = 19
        renderscriptSupportModeEnabled = true

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/arm64-v8a/libc++_shared.so"
            )
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
    namespace = "com.verazial.biometry"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    buildFeatures {
        //noinspection DataBindingWithoutKapt
        dataBinding = true
        buildConfig = true
    }
}

publishing {
    publications {
        register<MavenPublication>("biometry") {
            groupId = rootProject.group.toString()
            artifactId = "biometry"
            version = "1.0.3"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

dependencies {
    implementation("com.verazial.common-kotlin:core:1.0.1")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("com.verazial.ext-libs:iddk2000:3.6.3")
    //IRLINKER
    implementation("com.verazial.ext-libs:irisalgo:1.0")
    //region PixSur Integrated Sensor
    implementation("com.verazial.ext-libs:pirisbsp-release:1.0.0")
    //implementation("com.verazial.ext-libs:gmFaceIrisSdk-release:1.0.4")
    implementation("com.verazial.ext-libs:hid-android-release:2.3")
    //implementation("com.verazial.ext-libs:irissdk-release:1.0.0")
    //implementation("com.verazial.ext-libs:devicesdk-release:1.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    //endregion
    //region IDENTY
    //implementation("com.identy.app:finger:5.7.3")
    //implementation("com.android.volley:volley:1.2.1")
//    compileOnly("android.arch.lifecycle:viewmodel:1.1.1")
//    compileOnly("android.arch.lifecycle:livedata:1.1.1")
    compileOnly("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    compileOnly("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    //endregion
    implementation("com.verazial.ext-libs:ibscancommon-release:4.3.0")
    implementation("com.verazial.ext-libs:ibscanultimate-release:4.3.0")

    //Suprema BioMiniSDK
    implementation("com.verazial.ext-libs:libBioMini:3.0.3.3")
    // Laxton NFC
    implementation("com.verazial.ext-libs:idboxsdk:1.12.10")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    implementation("io.insert-koin:koin-core-jvm:3.3.3")

    implementation("com.juul.kable:core-android:0.23.0")

    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")

    implementation("com.google.mlkit:face-detection:16.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    androidTestImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("io.mockk:mockk:1.13.5")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.appcompat:appcompat:1.6.1")
    androidTestImplementation("io.kotest:kotest-assertions-core:5.7.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.5")
}