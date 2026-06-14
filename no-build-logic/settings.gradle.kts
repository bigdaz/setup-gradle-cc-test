plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.2.1"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.4.0"
}

develocity {
    server = "https://ge.solutions-team.gradle.com"
    buildScan {
        uploadInBackground = false
    }
}

rootProject.name = "no-build-logic"
include("lib")
