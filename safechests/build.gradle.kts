plugins {
    id("mc.plugin-conventions")
}

repositories {
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("org.popcraft:bolt-bukkit:1.2.22")
    compileOnly("org.popcraft:bolt-common:1.2.22")
}
