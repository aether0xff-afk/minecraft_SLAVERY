plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    java
}

group = "dev.aether0xff"
version = "0.1.0-SNAPSHOT"

repositories {
    maven("https://maven.supersanta.me/snapshots")
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.160.5+26.3")
    implementation("me.senseiwells:puppet-players:2.1.0+26.3")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}
