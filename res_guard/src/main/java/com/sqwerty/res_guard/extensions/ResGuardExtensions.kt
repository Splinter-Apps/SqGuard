package com.sqwerty.res_guard.extensions

import com.sqwerty.res_guard.utils.ResType
import org.gradle.api.GradleException

open class ResGuardExtensions {
    var enabled: Boolean = false

    var resTypes: List<ResType> = ResType.values().toList()

    /**
     * @author should include full path. Example: C:\Users\You
     */
    var outputMappingPath: String? = null

    /**
     * @throws GradleException value is less than 16
     */
    var minNameLength: Int = 25

    /**
     * @throws GradleException value is greater than 196
     */
    var maxNameLength: Int = 196

    /**
     * Path to the res directory relative to the project dir.
     * Defaults to "src/main/res". Set to "src/androidMain/res" for KMP projects.
     */
    var resDirPath: String? = null

    /**
     * Path to the source directory to scan for resource references, relative to the project dir.
     * Defaults to "src/main". Set to "src/androidMain" for KMP projects.
     */
    var sourceDirPath: String? = null
}