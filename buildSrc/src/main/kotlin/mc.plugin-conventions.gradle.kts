plugins {
    id("mc.java-conventions")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    // Each plugin gets its own copy of core; relocating keeps two plugins' copies from clashing
    // when one plugin depends on another (Tag -> Names).
    relocate("dev.sirbepy.mc.core", "dev.sirbepy.mc.${project.name}.libs.core")
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
