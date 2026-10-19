plugins {
    id("mc.plugin-conventions")
}

dependencies {
    // Send-only bot client: JDABuilder.createLight disables the privileged/member-cache intents
    // this plugin never needs, so no Discord Developer Portal intent toggles beyond the default.
    implementation("net.dv8tion:JDA:5.6.1")
}
