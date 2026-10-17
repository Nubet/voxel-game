plugins {
    application
    java
    id("com.diffplug.spotless") version "8.10.4"
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net") { content { includeModule("com.mojang", "brigadier") } }
}

val lwjglVersion = "3.4.3"
val jomlVersion = "1.10.8"
val os = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch").lowercase()
val natives = when {
    os.contains("win") -> if (arch.contains("aarch64") || arch.contains("arm64")) "natives-windows-arm64" else "natives-windows"
    os.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm64")) "natives-macos-arm64" else "natives-macos"
    else -> if (arch.contains("aarch64") || arch.contains("arm64")) "natives-linux-arm64" else "natives-linux"
}

dependencies {
    implementation("com.mojang:brigadier:1.0.500")
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb").forEach {
        implementation("org.lwjgl:$it")
        runtimeOnly("org.lwjgl:$it::$natives")
    }
    implementation("org.joml:joml:$jomlVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
application {
    mainClass.set("com.norbertfila.Main")
    if (os.contains("mac")) applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
}
tasks.test { useJUnitPlatform() }

tasks.register<JavaExec>("smokeTerrainUniforms") {
    group = "verification"
    description = "Checks active and optimized-out uniforms in all terrain shader variants."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.render.TerrainProgramSmoke")
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("smokeChat") {
    group = "verification"
    description = "Checks chat and debug overlays across viewport sizes and GUI scales."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.render.UiViewportSmoke")
    val output = layout.buildDirectory.dir("chat-smoke")
    doFirst { output.get().asFile.mkdirs() }
    args(output.get().asFile.absolutePath, "--chat-only")
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("smokeViewDistances") {
    group = "verification"
    description = "Checks distance fog on near/far solids and water, underwater visibility and PL/EN settings."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.lod.ViewDistanceSmoke")
    args(layout.buildDirectory.dir("view-distance-smoke").get().asFile.absolutePath)
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("smokeCrosshair") {
    group = "verification"
    description = "Checks crosshair styles, alpha and settings previews across viewport sizes and GUI scales."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.render.UiViewportSmoke")
    val output = layout.buildDirectory.dir("crosshair-smoke")
    doFirst { output.get().asFile.mkdirs() }
    args(output.get().asFile.absolutePath, "--crosshair-only")
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("smokeClouds") {
    group = "verification"
    description = "Checks cloud rendering, depth occlusion and state on OpenGL."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.render.CloudRendererSmoke")
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("debugBiomes") {
    group = "verification"
    description = "Writes a biome debug map to build/biome-debug/biomes.png."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.generation.BiomeDebugMap")
}

tasks.register<JavaExec>("smokeBlockModels") {
    group = "verification"
    description = "Checks outlines, mining effects and dropped-item rendering on OpenGL."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.render.BlockOutlineSmoke")
    val output = layout.buildDirectory.dir("block-model-smoke")
    doFirst { output.get().asFile.mkdirs() }
    args(output.get().asFile.absolutePath)
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("diagnoseLandscapes") {
    group = "verification"
    description = "Writes repeatable multi-seed landscape maps, distributions, transects and timings."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.generation.LandscapeDiagnostics")
    args(layout.buildDirectory.dir("landscape-diagnostics/${findProperty("label") ?: "current"}").get().asFile.absolutePath)
    findProperty("baseline")?.let { args(it) }
}

tasks.register<JavaExec>("generateHudAtlas") {
    group = "verification"
    description = "Regenerates the HUD atlas from registered block textures."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.ui.GenerateHudAtlas")
    args(layout.projectDirectory.file("src/main/resources/assets/ui/ui.png").asFile.absolutePath)
}

tasks.register<JavaExec>("smokeTerrainPasses") {
    group = "verification"
    description = "Checks split terrain passes against the combined renderer on OpenGL."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.render.TerrainPassSmoke")
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("profileCaves") {
    group = "verification"
    description = "Measures cold/warm cave generation time and allocation with a determinism check."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.generation.CaveGenerationProfile")
}

tasks.register<JavaExec>("smokeCaves") {
    group = "verification"
    description = "Renders cave interiors and natural entrances for three seeds."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.generation.CaveGenerationSmoke")
    args(layout.buildDirectory.dir("cave-smoke").get().asFile.absolutePath)
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

spotless {
    java {
        palantirJavaFormat("2.102.0")
    }
}

tasks.register<JavaExec>("benchmarkGame") {
    group = "verification"
    description = "Runs the scripted production-game benchmark (-Pscenes, -Prd, -Pfar, -Plabel)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.norbertfila.Main")
    doFirst {
        systemProperty("voxel.benchmark.revision", providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim())
        systemProperty("voxel.benchmark.dirty", providers.exec {
            commandLine("git", "status", "--porcelain", "--untracked-files=no")
        }.standardOutput.asText.get().isNotBlank())
    }
    jvmArgs("-Xmx4g")
    findProperty("jfr")?.let { jvmArgs("-XX:StartFlightRecording=filename=$it,settings=profile,dumponexit=true,maxsize=256m") }
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
    systemProperty("voxel.benchmark", findProperty("scenes") ?: "all")
    systemProperty("voxel.benchmark.rd", findProperty("rd") ?: "8")
    systemProperty("voxel.benchmark.label", findProperty("label") ?: "unlabeled")
    systemProperty("voxel.benchmark.gpuTimers", findProperty("gpuTimers") ?: "true")
    findProperty("seconds")?.let { systemProperty("voxel.benchmark.seconds", it) }
    findProperty("genWorkers")?.let { systemProperty("voxel.benchmark.genWorkers", it) }
    findProperty("meshWorkers")?.let { systemProperty("voxel.benchmark.meshWorkers", it) }
    findProperty("farWorkers")?.let { systemProperty("voxel.benchmark.farWorkers", it) }
    findProperty("directDraw")?.let { systemProperty("voxel.render.directDraw", it) }
    findProperty("width")?.let { systemProperty("voxel.benchmark.width", it) }
    findProperty("height")?.let { systemProperty("voxel.benchmark.height", it) }
    findProperty("far")?.let { systemProperty("voxel.benchmark.far", it) }
    findProperty("clouds")?.let { systemProperty("voxel.benchmark.clouds", it) }
    findProperty("screenshots")?.let { systemProperty("voxel.benchmark.screenshots", it) }
    findProperty("seed")?.let { systemProperty("voxel.benchmark.seed", it) }
    findProperty("siteX")?.let { systemProperty("voxel.benchmark.siteX", it) }
    findProperty("siteZ")?.let { systemProperty("voxel.benchmark.siteZ", it) }
    findProperty("out")?.let { systemProperty("voxel.benchmark.out", it) }
}

tasks.register<JavaExec>("smokeOres") {
    group = "verification"
    description = "Renders the five ore blocks with their REFI textures."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.generation.OreSmoke")
    args(layout.buildDirectory.dir("ore-smoke").get().asFile.absolutePath)
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("smokeBuildingBlocks") {
    group = "verification"
    description = "Renders building materials and all Creative catalog pages on OpenGL."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.generation.BuildingBlocksSmoke")
    args(layout.buildDirectory.dir("building-blocks-smoke").get().asFile.absolutePath)
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("smokeStreamingLifecycle") {
    group = "verification"
    description = "Checks session replacement, GPU cleanup, persistence and worker termination on OpenGL."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.core.SessionLifecycleSmoke")
    val output = layout.buildDirectory.dir("streaming-lifecycle-smoke")
    doFirst { output.get().asFile.mkdirs() }
    args(output.get().asFile.absolutePath)
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}

tasks.register<JavaExec>("smokeFarTerrain") {
    group = "verification"
    description = "Renders generated Far LOD from three viewpoints and checks OpenGL errors."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.norbertfila.lod.FarTerrainSmoke")
    val output = layout.buildDirectory.dir("far-terrain-smoke")
    doFirst { output.get().asFile.mkdirs() }
    args(output.get().asFile.absolutePath)
    if (os.contains("mac")) jvmArgs("-XstartOnFirstThread")
}
