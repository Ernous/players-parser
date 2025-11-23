plugins {
    kotlin("jvm") version "2.1.0"
    id("maven-publish")
    id("java-library")
}

group = "com.github.Ernous"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.json:json:20240303")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

// Configure tests
tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// Publishing configuration for JitPack
publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            
            pom {
                name.set("Players Parser")
                description.set("Kotlin library for parsing video player streams from various sources")
                url.set("https://github.com/Ernous/players-parser")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("Ernous")
                        name.set("Ernous")
                        url.set("https://github.com/Ernous")
                    }
                }
                
                scm {
                    connection.set("scm:git:https://github.com/Ernous/players-parser.git")
                    developerConnection.set("scm:git:https://github.com/Ernous/players-parser.git")
                    url.set("https://github.com/Ernous/players-parser")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Ernous/players-parser")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as? String
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as? String
            }
        }
    }
}
