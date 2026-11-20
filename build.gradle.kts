plugins {
    java
}

val mcVersion: String by project
val paperApiVersion: String by project
val javaVersion: String by project
val pluginVersion: String by project

group = "dev.stormrider"
version = pluginVersion

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val xwApi = file("libs/XmasWorld-api.jar")
val wgJar = file("libs/worldguard-bukkit-7.0.17.jar")
val faweJar = file("libs/FastAsyncWorldEdit-Paper-2.15.3.jar")
val gpJar = file("libs/GriefPrevention.jar")

// Compile-only stubs stand in for WorldGuard/WorldEdit/GriefPrevention when those
// jars are not in libs/. They are never packaged into the plugin jar.
sourceSets {
    create("wgStubs") { java.srcDir("stubs/worldguard") }
    create("gpStubs") { java.srcDir("stubs/griefprevention") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    "wgStubsCompileOnly"("io.papermc.paper:paper-api:$paperApiVersion")
    "gpStubsCompileOnly"("io.papermc.paper:paper-api:$paperApiVersion")

    // Author's XmasWorld API (committed). Required to compile Hooks.XW.
    compileOnly(files(xwApi))

    // Optional real integration jars from a server (not committed).
    if (wgJar.isFile) compileOnly(files(wgJar))
    if (faweJar.isFile) compileOnly(files(faweJar))
    if (gpJar.isFile) compileOnly(files(gpJar))

    // Stubs only when the real jar (and, for WorldGuard, WorldEdit/FAWE for BukkitAdapter) is missing.
    val needWgStubs = !wgJar.isFile || !faweJar.isFile
    val needGpStubs = !gpJar.isFile
    if (needWgStubs) compileOnly(sourceSets["wgStubs"].output)
    if (needGpStubs) compileOnly(sourceSets["gpStubs"].output)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion.toInt())
}

tasks.processResources {
    val props = mapOf("version" to pluginVersion, "apiVersion" to mcVersion)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveFileName.set("NaughtyList-$pluginVersion-mc$mcVersion.jar")
    // Do not ship stubs (they are only on the compileOnly classpath).
}

val dist by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar)
    into(layout.projectDirectory.dir("dist"))
}
tasks.build { finalizedBy(dist) }
