// ---------------------------------------------------------------------------------------------
// shiroikuma-ongakuots fork layer — versioning + the buildFork pipeline.
//
// Applied from the root build.gradle.kts (one added line there) so the whole fork-version
// computation lives in ONE file that upstream never touches, instead of being duplicated in
// :androidApp and :composeApp. Both modules read the results out of rootProject.extra.
//
// Upstream's own version literals (gradle/libs.versions.toml: version-name / version-code) are
// READ, never edited — an upstream bump therefore flows in by itself on a rebase, with no
// hand-editing and no conflict.
//
// See .claude/skills/build-apk and the global git-versioning skill.
// ---------------------------------------------------------------------------------------------

import org.gradle.api.artifacts.VersionCatalogsExtension
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val repoRootDir = rootProject.rootDir

// `providers.exec`, NOT a raw ProcessBuilder: with Gradle's configuration cache on, a process
// started at configuration time is refused outright. The provider API is the supported form and it
// registers the git output as a cache input, so the pin re-resolves when the base moves.
// isIgnoreExitValue: a tree with no local `master` (shallow clone, tarball) must degrade to an
// empty pin, never fail the build.
fun gitOutput(vararg command: String): String =
    try {
        providers
            .exec {
                commandLine(*command)
                workingDir = repoRootDir
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
    } catch (e: Exception) {
        println("Git command [${command.joinToString(" ")}] failed [$e]")
        ""
    }

// Upstream-base pin. `master` mirrors the bleeding upstream/dev, whose versionName stands still for
// months, so the sha is what says whether we are behind upstream. It is the merge-base of HEAD and
// master — the upstream commit our patches sit on — NOT our own HEAD (that identifies one of OUR
// commits, which +N already covers) and NOT master's tip (which overstates the base whenever
// `custom` has not been rebased yet).
val upstreamBaseSha = gitOutput("git", "merge-base", "HEAD", "master").take(8)

// That same commit's committer date, so versions sort chronologically: a bare sha orders builds at
// random and a bare date ties whenever two syncs land on one day. Committer date, never build time
// — every build on one upstream base must share a pin. Rendered in UTC from the raw epoch so it
// matches what anything reading the version back from the GitHub API would compute.
val upstreamBaseStamp =
    if (upstreamBaseSha.length == 8) {
        gitOutput("git", "show", "-s", "--format=%ct", upstreamBaseSha).toLongOrNull()?.let {
            Instant
                .ofEpochSecond(it)
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd.HH-mm"))
        } ?: ""
    } else {
        ""
    }

// `+` opens each top-level group; the pin's own date, time and sha are dot-joined inside it.
val upstreamPin =
    when {
        upstreamBaseSha.length != 8 -> ""
        upstreamBaseStamp.length == 16 -> "+$upstreamBaseStamp.g$upstreamBaseSha"
        else -> "+g$upstreamBaseSha" // git present but the timestamp lookup failed
    }

val catalog = rootProject.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
val upstreamVersionName = catalog.findVersion("version-name").get().requiredVersion
val upstreamVersionCode = catalog.findVersion("version-code").get().requiredVersion.toInt()

val forkBuildNumber = (providers.gradleProperty("BUILD_NUMBER").orNull ?: "1").trim().toInt()
val forkLastBuiltVersionCode = (providers.gradleProperty("LAST_BUILT_VERSION_CODE").orNull ?: "0").trim().toInt()

// Zero-padded in the NAME only, so +002 sorts before +010; versionCode keeps the plain integer.
val forkVersionName = "$upstreamVersionName$upstreamPin+${forkBuildNumber.toString().padStart(3, '0')}"
val forkVersionCode = upstreamVersionCode * 10000 + forkBuildNumber

rootProject.extra["forkVersionName"] = forkVersionName
rootProject.extra["forkVersionCode"] = forkVersionCode
rootProject.extra["forkBuildNumber"] = forkBuildNumber
rootProject.extra["forkUpstreamVersionCode"] = upstreamVersionCode

// --- buildFork ---------------------------------------------------------------------------------
// Build the signed release APK, copy it to ~/tmp under the house filename, then bump BUILD_NUMBER
// and record the code we just shipped.
tasks.register("buildFork") {
    group = "build"
    description = "Build the signed release APK, copy it to ~/tmp, and bump BUILD_NUMBER."
    dependsOn(":androidApp:assembleRelease")

    // Captured at configuration time — the configuration cache forbids touching `project` from a
    // task action.
    val releaseApkDir = File(repoRootDir, "androidApp/build/outputs/apk/release")
    val propsFile = File(repoRootDir, "gradle.properties")
    val targetDir = File(System.getProperty("user.home"), "tmp")
    val apkName = "shiroikuma-ongakuots_${forkVersionName}_arm64-v8a.apk"
    val builtCode = forkVersionCode
    val lastBuiltCode = forkLastBuiltVersionCode
    val nextBuildNumber = forkBuildNumber + 1

    doFirst {
        // `master` tracks the bleeding upstream/dev, whose versionCode stands still between
        // upstream releases — so BUILD_NUMBER never resets. This guard turns the one way it could
        // still go backwards (a hand-edited counter) into a build failure rather than a silent
        // downgrade the installer would refuse.
        if (builtCode <= lastBuiltCode) {
            throw GradleException(
                "versionCode $builtCode would not exceed the highest already built ($lastBuiltCode). " +
                    "Raise BUILD_NUMBER past the last built tail.",
            )
        }
    }

    doLast {
        val apk =
            releaseApkDir
                .listFiles { _, name -> name.endsWith(".apk") }
                ?.minByOrNull { it.name }
                ?: throw GradleException("No APK found in $releaseApkDir")

        targetDir.mkdirs()
        val target = File(targetDir, apkName)
        apk.copyTo(target, overwrite = true)
        println("[1;36m>>> ${target.absolutePath}[0m")
        println("[1;36m>>> versionCode $builtCode[0m")

        propsFile.writeText(
            propsFile
                .readText()
                .replace(Regex("(?m)^BUILD_NUMBER=\\d+$"), "BUILD_NUMBER=$nextBuildNumber")
                .replace(Regex("(?m)^LAST_BUILT_VERSION_CODE=\\d+$"), "LAST_BUILT_VERSION_CODE=$builtCode"),
        )
        println("[1;36m>>> BUILD_NUMBER bumped to $nextBuildNumber[0m")
    }
}
