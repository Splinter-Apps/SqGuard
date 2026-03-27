package com.sqwerty.res_guard.tasks.res_deobfuscation

import com.sqwerty.core.utils.SqTask
import com.sqwerty.core.utils.getAllProjectFilesViaRecursion
import com.sqwerty.res_guard.extensions.ResGuardExtensions
import com.sqwerty.res_guard.utils.Helper
import com.sqwerty.res_guard.utils.Helper.getResGuardMap
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
import kotlin.reflect.jvm.jvmName

open class ResGuardDecoder : SqTask() {
    override fun Task.doLast() {
        val resources = getResources(project)
        val resGuardMap = getResGuardMap(project).toList()
            .associate { (key, value) -> value to key }
        val s = File.separator
        val sourceDirPath = project.extensions.getByType(ResGuardExtensions::class.java).sourceDirPath
            ?: "src${s}main"

        runBlocking(Dispatchers.IO) {
            val asyncResUpdating = resources.groupBy { res -> res.nameWithoutExtension }.values
                .map { grouped ->
                    launch {
                        val res = grouped.first()
                        val baseName = resGuardMap.getValue(res.nameWithoutExtension)
                        grouped.forEach {
                            it.updateFileName(baseName, project)
                        }
                    }
                }
            asyncResUpdating.joinAll()
            getAllProjectFilesViaRecursion(project.layout.projectDirectory.dir(sourceDirPath).asFile)
                .forEach { file ->
                    if (file.isInBlacklist(project)) return@forEach
                    file.readText().apply {
                        var updatedText = this
                        resGuardMap.forEach { (key, value) ->
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
        return Helper.getResGuardMappingFile(project).exists()
    }

    companion object : SqTaskCompanion() {
        override fun Project.addToTaskSequence() {
            tasks.named("assembleRelease") { finalizedBy(taskKClass.jvmName) }
            tasks.named("bundleRelease") { finalizedBy(taskKClass.jvmName) }
        }
    }
}