package com.sqwerty.res_guard.extensions

open class ResResizeExtensions {
    /**
     * Example: C:\Users\You\magick
     */
    var pathToMagick: String? = null

    /**
     * Resizes all png resources, even if they don't fit 1080x1980 or 1980x1080.
     * Also useful when adding a new image to resized resources
     */
    var resizeHard: Boolean = false

    var enabled: Boolean = false

    /**
     * Path to the res directory relative to the project dir.
     * Defaults to "src/main/res". Set to "src/androidMain/res" for KMP projects.
     */
    var resDirPath: String? = null
}