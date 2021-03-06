package com.peterlaurence.trekme.util

import android.text.format.DateUtils

/**
 * Given a distance in meters, format this distance to return a value expressed either in meters
 * or in km.
 */
fun formatDistance(dist: Double): String {
    return if (dist <= 1000) {
        "%.0f m".format(dist)
    } else {
        "%.2f km".format(dist / 1000.0)
    }
}

fun formatDuration(durationInSec: Long): String {
    return DateUtils.formatElapsedTime(durationInSec)
}
