plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "net.azisaba"
version = "1.2.0+1.21.11"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        name = "Lumine Releases"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }
    maven {
        name = "azisaba-repo"
        url = uri("https://repo.azisaba.net/repository/maven-public/")
    }
    maven {
        name = "codemc-repo"
        url = uri("https://repo.codemc.io/repository/maven-releases/")
    }
    maven {
        name = "jitpack-repo"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.12.0")
    compileOnly("net.azisaba:lifepvelevel:1.3.0")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude("org.bukkit", "bukkit")
    }

    implementation(kotlin("stdlib"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        relocate("kotlin", "net.azisaba.lifepveassist.lib.kotlin")
        relocate("org.jetbrains", "net.azisaba.lifepveassist.lib.org.jetbrains")
    }

    jar {
        archiveClassifier.set("plain")
    }

    assemble {
        dependsOn(shadowJar)
    }
}
