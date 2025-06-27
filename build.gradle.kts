import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    java
    alias(libs.plugins.pluginYml)
}

group = "com.github.gpaddons"
version = "1.0.2-SNAPSHOT"
description = "A GriefPrevention addon for pet transfers."

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        url = uri("https://jitpack.io")
    }
    mavenLocal()
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.griefPrevention)
}

bukkit {
    main = "com.github.gpaddons.givepet.GivePet"
    apiVersion = libs.versions.paperApi.get().replace(Regex("\\-R\\d.\\d-SNAPSHOT"), "")
    authors = listOf("Jim (AnEnragedPigeon)", "Jikoo")
    description = "A GriefPrevention addon for pet transfers."
    depend = listOf("GriefPrevention")

    permissions {
        register("givepet.give") {
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("givepet.receive") {
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
    }

    commands {
        register("givepet") {
          aliases = listOf("transferpet")
          description = "Give someone a tamed animal of yours!"
          permission = "givepet.give"
          usage = "/<command> <player>"
        }
        register("acceptpet") {
          description = "Accept a gifted pet!"
          permission = "givepet.receive"
          usage = "/<command>"
        }
        register("declinepet") {
          description = "Decline a gifted pet."
          permission = "givepet.receive"
          usage = "/<command>"
        }
    }
}

tasks {
    compileJava {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        options.encoding = "UTF-8"
    }
}
