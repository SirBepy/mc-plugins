import java.io.File

// Shared between server/build.gradle.kts' assemble-mode tasks and its deployBundle convention
// plugin (mc.server-deploy-bundle.gradle.kts): both copy a source tree into a dist/staging dir.
fun copyTree(src: File, destRoot: File, exclude: (File) -> Boolean = { false }) {
    if (!src.exists()) return
    src.walkTopDown().filter { it.isFile && !exclude(it) }.forEach { file ->
        val target = destRoot.resolve(file.relativeTo(src).path)
        target.parentFile.mkdirs()
        file.copyTo(target, overwrite = true)
    }
}

val isReadmeFile: (File) -> Boolean = { it.name == "README.md" }
