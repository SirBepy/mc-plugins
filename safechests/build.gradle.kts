plugins {
    id("mc.plugin-conventions")
}

repositories {
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("org.popcraft:bolt-bukkit:1.2.22")
    compileOnly("org.popcraft:bolt-common:1.2.22")
    // compileOnly does not propagate to the test source set; tests need Bolt's types too
    // (a fake BoltAPI implementation, Protection/Source construction), and paper-api itself
    // since MockBukkit's own mock types implement its interfaces rather than bundling them.
    testImplementation("org.popcraft:bolt-bukkit:1.2.22")
    testImplementation("org.popcraft:bolt-common:1.2.22")
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}
