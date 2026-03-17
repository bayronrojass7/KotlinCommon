buildscript {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0-rc03")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

group = "com.verazial.common-kotlin"

tasks {
    withType(JavaCompile::class) {
        options.compilerArgs.addAll(arrayOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }
    withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).configureEach {
        kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
    register("clean", Delete::class) {
        delete(rootProject.buildDir)
    }
}