import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription

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

paper {
    main = "com.github.gpaddons.givepet.GivePet"
    apiVersion = libs.versions.paperApi.get().replace(Regex("\\-R\\d.\\d-SNAPSHOT"), "")
    authors = listOf("Jim (AnEnragedPigeon)", "Jikoo")
    description = "A GriefPrevention addon for pet transfers."

    serverDependencies {
      register("GriefPrevention") {
        required = true
      }
    }

    permissions {
        register("givepet.give") {
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("givepet.receive") {
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
    }
}

tasks {
    compileJava {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        options.encoding = "UTF-8"
    }
}
