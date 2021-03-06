import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.72"
    id("com.github.hierynomus.license") version "0.15.0"
    id("com.gradle.plugin-publish") version "0.12.0"
    `kotlin-dsl`
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint") version "9.4.0"
}

group = "au.id.jpg"
version = "1.0.1"

repositories {
    jcenter()
}

dependencies {
    implementation("com.google.cloud:google-cloud-storage:1.113.0")
    implementation(kotlin("stdlib-jdk8"))
}

ktlint {
    disabledRules.set(setOf("final-newline", "no-wildcard-imports"))
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
}

gradlePlugin {
    plugins {
        create("gcsBuildCache") {
            id = "au.id.jpg.gradle-gcs-build-cache"
            implementationClass = "au.id.jpg.gradle.caching.GCSBuildCachePlugin"
            displayName = "GCS Build Cache"
            description = """
                A Gradle build cache implementation that uses Google Cloud Storage (GCS) to store the build artifacts.
                Since this is a settings plugin the build script snippets below won't work.
                Please consult the documentation at Github.
            """.trimIndent()
        }
    }
}

pluginBundle {
    website = "https://github.com/josephglanville/gradle-gcs-build-cache"
    vcsUrl = "https://github.com/josephglanville/gradle-gcs-build-cache.git"
    tags = listOf("build-cache", "gcs", "Google Cloud Storage", "cache")
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}
