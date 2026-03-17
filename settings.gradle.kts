pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        /*maven {
            url = uri("https://identy.jfrog.io/identy/identy-finger-release")
            credentials {
                username = "verazial"
                password = "ly8Hd5rvOiihFd"
            }
        }*/
        maven {
            url = uri("${rootDir}/biometry/maven-repo")
        }
    }
}
rootProject.name = "KotlinCommon"
include(":biometry")
include(":server")
include(":core")
include(":biometry-test")
