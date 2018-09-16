package com.peterlaurence.trekadvisor.core.track

import android.content.ContentResolver
import android.net.Uri
import android.os.AsyncTask
import com.peterlaurence.trekadvisor.core.TrekAdvisorContext.DEFAULT_RECORDINGS_DIR
import com.peterlaurence.trekadvisor.core.map.Map
import com.peterlaurence.trekadvisor.core.map.gson.MarkerGson
import com.peterlaurence.trekadvisor.core.map.gson.RouteGson
import com.peterlaurence.trekadvisor.util.gpx.GPXParser
import com.peterlaurence.trekadvisor.util.gpx.model.Track
import com.peterlaurence.trekadvisor.util.gpx.model.TrackSegment
import java.io.*
import java.util.*

/**
 * Utility toolbox to :
 *
 *  * Import a gpx track file into a [Map].
 *  * Get the list of gpx files created by location recording.
 *
 *
 * @author peterLaurence on 03/03/17 -- converted to Kotlin on 16/09/18
 */
object TrackImporter {
    private val supportedTrackFilesExtensions = arrayOf("gpx", "xml")

    private val SUPPORTED_FILE_FILTER = filter@{ dir: File, filename: String ->
        /* We only look at files */
        if (File(dir, filename).isDirectory) {
            return@filter false
        }

        supportedTrackFilesExtensions.any { filename.endsWith(".$it") }
    }

    /**
     * Get the list of [File] which extension is in the list of supported extension for track
     * file. Files are searched into the
     * [com.peterlaurence.trekadvisor.core.TrekAdvisorContext.DEFAULT_RECORDINGS_DIR].
     */
    val recordings: Array<File>?
        get() = DEFAULT_RECORDINGS_DIR.listFiles(SUPPORTED_FILE_FILTER)

    fun isFileSupported(uri: Uri): Boolean {
        val path = uri.path
        val extension = path.substring(path.lastIndexOf(".") + 1)

        if ("" == extension) return false

        return supportedTrackFilesExtensions.any { it == extension }
    }

    /**
     * Parse a [File] that contains routes, and is in one of the supported formats. <br></br>
     * The parsing is done in an asynctask.
     *
     * @param uri      the track as an [Uri]
     * @param listener a [TrackFileParsedListener]
     * @param map      the [Map] to which the routes will be added.
     */
    fun importTrackUri(uri: Uri, listener: TrackFileParsedListener, map: Map,
                       contentResolver: ContentResolver) {

        try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            if (parcelFileDescriptor == null) {
                listener.onError("Could not read content of file")
                return
            }
            val fileDescriptor = parcelFileDescriptor.fileDescriptor
            val fileInputStream = FileInputStream(fileDescriptor)

            val gpxTrackFileToRoutesTask = GpxTrackFileToRoutesTask(listener, map, Runnable {
                try {
                    parcelFileDescriptor.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            })
            gpxTrackFileToRoutesTask.execute(fileInputStream)
        } catch (e: IOException) {
            listener.onError("Error when opening the file")
        }

    }

    fun importTrackFile(file: File, listener: TrackFileParsedListener, map: Map) {
        try {
            val fileInputStream = FileInputStream(file)

            val gpxTrackFileToRoutesTask = GpxTrackFileToRoutesTask(listener, map, null)
            gpxTrackFileToRoutesTask.execute(fileInputStream)
        } catch (e: FileNotFoundException) {
            listener.onError("The file doesn't exists")
        }

    }

    interface TrackFileParsedListener {
        fun onTrackFileParsed(map: Map, routeList: List<@JvmSuppressWildcards RouteGson.Route>)

        fun onError(message: String)
    }

    private class GpxTrackFileToRoutesTask internal constructor(private val mListener: TrackFileParsedListener, private val mMap: Map, private val mPostExecuteTask: Runnable?) : AsyncTask<InputStream, Void, Void?>() {
        private val mNewRouteList: LinkedList<RouteGson.Route> = LinkedList()

        /**
         * Each gpx file may contain several tracks. And each [Track] may contain several
         * [TrackSegment]. <br></br>
         * A [Track] is the equivalent of a [RouteGson.Route], so all [TrackSegment]
         * are added to a single [RouteGson.Route].
         */
        override fun doInBackground(vararg inputStreamList: InputStream): Void? {
            for (stream in inputStreamList) {

                try {
                    val gpx = GPXParser.parse(stream)

                    for (track in gpx.tracks) {
                        val route = gpxTracktoRoute(track)
                        mNewRouteList.add(route)
                    }
                    stream.close()
                } catch (e: Exception) {
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    e.printStackTrace(pw)
                    mListener.onError(sw.toString())
                }
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            mListener.onTrackFileParsed(mMap, mNewRouteList)
            mPostExecuteTask?.run()
        }

        /**
         * Converts a [Track] into a [RouteGson.Route]. <br></br>
         * A single [Track] may contain several [TrackSegment].
         */
        private fun gpxTracktoRoute(track: Track): RouteGson.Route {
            /* Create a new route */
            val route = RouteGson.Route()

            /* The route name is the track name */
            route.name = track.name

            /* The route should be visible by default */
            route.visible = true

            /* All track segments are concatenated */
            val trackSegmentList = track.trackSegments
            for (trackSegment in trackSegmentList) {
                val trackPointList = trackSegment.trackPoints
                for (trackPoint in trackPointList) {
                    val marker = MarkerGson.Marker()

                    /* If the map uses a projection, store projected values */
                    val projectedValues: DoubleArray?
                    val projection = mMap.projection
                    if (projection != null) {
                        projectedValues = projection.doProjection(trackPoint.latitude, trackPoint.longitude)
                        if (projectedValues != null) {
                            marker.proj_x = projectedValues[0]
                            marker.proj_y = projectedValues[1]
                        }
                    }

                    /* In any case, we store the wgs84 coordinates */
                    marker.lat = trackPoint.latitude
                    marker.lon = trackPoint.longitude

                    route.route_markers.add(marker)
                }
            }
            return route
        }
    }
}
