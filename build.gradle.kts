plugins {
    java
    jacoco
    `maven-publish`
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("me.champeau.jmh") version "0.7.2"
}

group = "com.vamva"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) {
            exclude("com/vamva/ratelimiter/RateLimiterApplication.class")
            exclude("com/vamva/ratelimiter/demo/**")
        }
    }))
}

jmh {
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    resultFormat.set("JSON")
    resultsFile.set(project.file("build/reports/jmh/results.json"))
}

// Library JAR: exclude demo app classes, produce plain JAR for consumers
tasks.jar {
    enabled = true
    archiveClassifier.set("")
    exclude("com/vamva/ratelimiter/RateLimiterApplication.class")
    exclude("com/vamva/ratelimiter/demo/**")
}

// Boot JAR: only for running the demo app locally
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("demo")
}

// ── Publishing ───────────────────────────────────────────────────────────

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("API Rate Limiter")
                description.set("Distributed API rate limiter middleware with Redis-backed enforcement for Spring Boot")
                url.set("https://github.com/KostasVam/api-rate-limiter")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("KostasVam")
                        name.set("Kostas Vamvakousis")
                    }
                }

                scm {
                    url.set("https://github.com/KostasVam/api-rate-limiter")
                    connection.set("scm:git:git://github.com/KostasVam/api-rate-limiter.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/KostasVam/api-rate-limiter")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String? ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String? ?: ""
            }
        }
    }
}
