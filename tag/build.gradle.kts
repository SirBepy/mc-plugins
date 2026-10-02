plugins {
    id("mc.plugin-conventions")
}

dependencies {
    compileOnly(project(":names"))
    testImplementation(project(":names"))
    // compileOnly("io.papermc.paper:paper-api") isn't on the test classpath by default; the state
    // round-trip test needs the real YamlConfiguration/InvalidConfigurationException implementations
    // (not provided by mockbukkit's own classpath) to actually run, and HeadProtectionListenerTest
    // builds real InventoryClickEvent/ItemStack/Material instances, which need paper-api at compile
    // time too, not just runtime.
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
}
