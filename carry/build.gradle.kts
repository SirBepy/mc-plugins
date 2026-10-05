plugins {
    id("mc.plugin-conventions")
}

repositories {
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.glaremasters.me/repository/towny/")
}

dependencies {
    compileOnly("org.popcraft:bolt-bukkit:1.2.22")
    compileOnly("org.popcraft:bolt-common:1.2.22")
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
    // Tests stub the protection checks behind CarryGuard, so only paper-api is needed there
    // (compileOnly isn't inherited by the test source set, same fix as tweaks/build.gradle.kts).
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}
