plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.serialization") version "1.8.0"
    id("com.google.devtools.ksp") version "1.8.10-1.0.9"
    id("maven-publish")
    signing
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        register<MavenPublication>("core") {
            groupId = rootProject.group.toString()
            artifactId = "core"
            version = "1.0.1"

            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")

    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.14.0")
    implementation("com.squareup.moshi:moshi:1.14.0")
}