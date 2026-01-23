package com.sqwerty.res_guard.tasks.res_obfuscation

import com.sqwerty.core.utils.SqTask
import com.sqwerty.core.utils.getAllProjectFilesViaRecursion
import com.sqwerty.res_guard.extensions.ResGuardExtensions
import com.sqwerty.res_guard.tasks.res_obfuscation.ResGuardEncryptor.encryptValue
import com.sqwerty.res_guard.utils.Helper
import com.sqwerty.res_guard.utils.Helper.getResGuardMap
import com.sqwerty.res_guard.utils.Helper.getResources
import com.sqwerty.res_guard.utils.Helper.isInBlacklist
import com.sqwerty.res_guard.utils.Helper.replaceRes
import com.sqwerty.res_guard.utils.Helper.updateFileName
import com.sqwerty.res_guard.utils.ResType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import java.io.FileOutputStream
import kotlin.reflect.jvm.jvmName

open class ResGuardEncoder : SqTask() {
    private val mappingFile = Helper.getResGuardMappingFile(project)

    override fun Task.doLast() {
        val mappingFOS = FileOutputStream(mappingFile, false)
        val resources = getResources(project)
        val scope = CoroutineScope(Dispatchers.IO)

        val asyncResUpdating = resources.groupBy { res -> res.nameWithoutExtension }.values
            .map { grouped ->
                scope.launch {
                    val res = grouped.first()

                    val encryptedValue = let {
                        var encryptedName = res.nameWithoutExtension.encryptValue(project)
                        while (mappingFile.readText().contains(encryptedName)) {
                            encryptedName = res.nameWithoutExtension.encryptValue(project)
                        }
                        encryptedName
                    }
                    mappingFOS.writeValue(res.nameWithoutExtension, encryptedValue)
                    grouped.forEach {
                        it.updateFileName(encryptedValue, project)
                    }
                }
            }
        scope.launch {
            asyncResUpdating.joinAll()
            mappingFOS.close()
            val s = File.separator
            val latestMapping = getResGuardMap(project)
            getAllProjectFilesViaRecursion(project.layout.projectDirectory.dir("src${s}main").asFile)
                .forEach { file ->
                    if (file.isInBlacklist(project)) return@forEach
                    file.readText().apply {
                        var updatedText = this
                        latestMapping.forEach { (key, value) ->
                            updatedText = ResType.values().fold(updatedText) { prev, resType ->
                                prev.replaceRes(resType, key, value)
                            }
                        }
                        file.writeText(updatedText)
                    }
                }
        }
    }

    private fun FileOutputStream.writeValue(value: String, encryptedValue: String) {
        val pair = "${value}->${encryptedValue}\n"
        write(pair.encodeToByteArray())
    }

    override fun Task.onlyIf(): Boolean {
        return project.extensions.getByType(ResGuardExtensions::class.java).enabled.also {
            if (it.not()) Helper.getResGuardMappingFile(project).delete()
        }
    }

    companion object : SqTaskCompanion() {
        override fun Project.addToTaskSequence() {
            val taskNames = project.gradle.startParameter.taskNames
            println("TasksNames: $taskNames")
            if (taskNames.any { it.lowercase().contains("release") }) {
                println("ResGuardEncoder ADDED")
                tasks.named("preBuild") { dependsOn(taskKClass.jvmName) }
            } else {
                println("ResGuardEncoder SKIPPED")
            }
        }
    }
}