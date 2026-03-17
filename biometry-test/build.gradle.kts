plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.verazial.biometry_test"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.verazial.biometry_test"
        minSdk = 25 // change it to 28 if it is going to be used in gm50
        targetSdk = 34
        versionCode = 2
        versionName = "v1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("verazialKey") {
            storeFile = file("C:\\Program Files (x86)\\Verázial Labs\\Tools\\Verazial.keystore")
            storePassword = "a14544200"
            keyAlias = "verazialkey"
            keyPassword = "a14544200"
        }
        create("irlinker") {
            storeFile = file("C:\\Program Files (x86)\\Verázial Labs\\Tools\\zklh.keystore")
            storePassword = "SykeanEmbed206"
            keyAlias = "sykeankeystore"
            keyPassword = "SykeanEmbed206"
            enableV1Signing = true
            enableV2Signing = true
        }
        create("pixsur") {
            storeFile = file("C:\\Program Files (x86)\\Verázial Labs\\Tools\\pixsur_out.keystore")
            storePassword = "pixsur"
            keyAlias = "pixsur_out"
            keyPassword = "pixsur"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("verazialKey")
            isDebuggable = true
        }
        debug {
            signingConfig = signingConfigs.getByName("verazialKey")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }

    packaging {
        jniLibs.pickFirsts.add("**/*.so")
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    implementation("com.verazial.common-kotlin:core:1.0.1")
    implementation(project(":biometry"))
    //implementation("com.verazial.common-kotlin:biometry:1.0.1")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(platform("androidx.compose:compose-bom:2023.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.12.0")
    //implementation("com.verazial.ext-libs:gmFaceIrisSdk-release:1.0.4")

    implementation("com.google.accompanist:accompanist-permissions:0.31.3-beta")
    implementation("io.insert-koin:koin-core-jvm:3.3.3")
    implementation("io.kotest:kotest-assertions-core:5.7.0")
    implementation("io.mockk:mockk:1.13.5")
    implementation("io.mockk:mockk-android:1.13.5")
}