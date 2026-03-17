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
        register<MavenPublication>("server") {
            groupId = rootProject.group.toString()
            artifactId = "server"
            version = "1.0.1"

            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}

dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.14.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.moshi:moshi:1.14.0")
}