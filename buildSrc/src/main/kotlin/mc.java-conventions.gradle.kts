plugins {
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.116.1")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    // build/ reports are unreadable in Claude sessions, so per-test lines in the console are the
    // only way to count passes and spot MockBukkit's silent SKIPPEDs.
    testLogging {
        events("passed", "skipped", "failed")
    }
}
