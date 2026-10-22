import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import watchface.WatchFaceDocument

/** Writes `raw/watchface.xml` into a generated res directory for one Watch Face Format version. */
@CacheableTask
abstract class GenerateWatchFace : DefaultTask() {

    @get:Input
    abstract val formatVersion: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.file("raw/watchface.xml").get().asFile
        file.parentFile.mkdirs()
        file.writeText(WatchFaceDocument(formatVersion.get()).render())
    }
}
