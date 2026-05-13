package com.sqwerty.res_guard.tasks.res_obfuscation

import com.sqwerty.core.utils.SqTask
import com.sqwerty.core.utils.getAllProjectFilesViaRecursion
import com.sqwerty.res_guard.extensions.ResGuardExtensions
import com.sqwerty.res_guard.tasks.res_obfuscation.ResGuardEncryptor.encryptValue
import com.sqwerty.res_guard.utils.Helper
import com.sqwerty.res_guard.utils.Helper.getResources
import com.sqwerty.res_guard.utils.Helper.isInBlacklist
import com.sqwerty.res_guard.utils.Helper.replaceRes
import com.sqwerty.res_guard.utils.Helper.updateFileName
import com.sqwerty.res_guard.utils.ResType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.jvm.jvmName

open class ResGuardEncoder : SqTask() {
    private val mappingFile = Helper.getResGuardMappingFile(project)

    override fun Task.doLast() {
        val resources = getResources(project)
        val s = File.separator
        val sourceDirPath = project.extensions.getByType(ResGuardExtensions::class.java).sourceDirPath
            ?: "src${s}main"

        val mapping = ConcurrentHashMap<String, String>()
        val usedEncryptedNames: MutableSet<String> = ConcurrentHashMap.newKeySet()

        runBlocking(Dispatchers.IO) {
            val asyncResUpdating = resources.groupBy { res -> res.nameWithoutExtension }.values
                .map { grouped ->
                    launch {
                        val res = grouped.first()
                        val originalName = res.nameWithoutExtension

                        var encryptedName = originalName.encryptValue(project)
                        while (!usedEncryptedNames.add(encryptedName)) {
                            encryptedName = originalName.encryptValue(project)
                        }
                        mapping[originalName] = encryptedName
                        grouped.forEach {
                            it.updateFileName(encryptedName, project)
                        }
                    }
                }
            asyncResUpdating.joinAll()

            mappingFile.writeText(
                buildString {
                    mapping.forEach { (original, encrypted) ->
                        append(original).append("->").append(encrypted).append('\n')
                    }
                }
            )

            getAllProjectFilesViaRecursion(project.layout.projectDirectory.dir(sourceDirPath).asFile)
                .forEach { file ->
                    if (file.isInBlacklist(project)) return@forEach
                    file.readText().apply {
                        var updatedText = this
                        mapping.forEach { (key, value) ->
                            updatedText = ResType.values().fold(updatedText) { prev, resType ->
                                prev.replaceRes(resType, key, value)
                            }
                        }
                        file.writeText(updatedText)
                    }
                }
        }
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