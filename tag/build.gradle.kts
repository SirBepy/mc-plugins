plugins {
    id("mc.plugin-conventions")
}

dependencies {
    compileOnly(project(":names"))
    testImplementation(project(":names"))
}
