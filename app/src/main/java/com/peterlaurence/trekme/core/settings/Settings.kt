package com.peterlaurence.trekme.core.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.peterlaurence.trekme.core.TrekMeContext
import com.peterlaurence.trekme.util.FileUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Holds global settings of TrekMe and exposes public methods to read/update those settings.
 * This class internally uses [SharedPreferences].
 */
@Singleton
class Settings @Inject constructor(private val trekMeContext: TrekMeContext, private val app: Application) {
    private val sharedPref: SharedPreferences = app.applicationContext.getSharedPreferences(settingsFile, Context.MODE_PRIVATE)
    private val appDirKey = "appDir"
    private val startOnPolicy = "startOnPolicy"
    private val favoriteMaps = "favoriteMaps"
    private val rotationMode = "rotationMode"
    private val magnifyingFactor = "magnifyingFactor"
    private val lastMapId = "lastMapId"
    private val defineScaleWhenCentered = "defineScaleWhenCentered"
    private val scaleCentered = "scaleCentered"

    /**
     * This is temporary migration code from the old settings mechanism to the new one which is
     * based on shared preferences. Was introduced in 2.2.8
     * TODO: Remove this once most users upgraded.
     */
    init {
        try {
            val file = trekMeContext.getSettingsFile(app.applicationContext)
            if (file.exists()) {
                val json = FileUtils.getStringFromFile(file)
                val oldSettings = Json.parse(SettingsData.serializer(), json)
                with(oldSettings) {
                    setAppDir(File(appDir))
                    setStartOnPolicy(startOnPolicy)
                    setDefineScaleCentered(defineScaleWhenCenter)
                    setScaleCentered(scaleCentered)
                    setFavoriteMapIds(favoriteMaps)
                    setLastMapId(lastMapId)
                    setMagnifyingFactor(magnifyingFactor)
                    setRotationMode(rotationMode)
                }

                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get the current application directory as [File].
     * This implementation tries to get it from the configuration file. Since this file might have
     * been modified by an human, a check is done to fallback to the default application directory
     * if the path isn't among the list of possible paths.
     * It's also a security in the case the application directories change across versions.
     */
    fun getAppDir(): File? {
        return sharedPref.getString(appDirKey, null)?.let {
            if (checkAppPath(it)) File(it) else null
        } ?: trekMeContext.defaultAppDir
    }

    /**
     * Set the application directory - the folder in which new maps are downloaded and also the
     * folder walked on app startup to fetch the list of maps.
     */
    fun setAppDir(file: File) {
        if (checkAppPath(file.absolutePath)) {
            sharedPref.edit {
                putString(appDirKey, file.absolutePath)
            }
        }
    }

    private fun checkAppPath(path: String): Boolean {
        return trekMeContext.mapsDirList?.map {
            it.absolutePath
        }?.contains(path) ?: false
    }

    /**
     * The [StartOnPolicy] defines whether TrekMe should boot on the map list or on the last map.
     */
    fun getStartOnPolicy(): StartOnPolicy {
        return sharedPref.getString(startOnPolicy, null)?.let {
            StartOnPolicy.valueOf(it)
        } ?: StartOnPolicy.MAP_LIST
    }

    fun setStartOnPolicy(policy: StartOnPolicy) {
        sharedPref.edit {
            putString(startOnPolicy, policy.name)
        }
    }

    fun setMagnifyingFactor(factor: Int) {
        sharedPref.edit {
            putInt(magnifyingFactor, factor)
        }
    }

    fun getMagnifyingFactor(): Int = sharedPref.getInt(magnifyingFactor, 0)

    /**
     * Get the rotation behavior when viewing a map.
     */
    fun getRotationMode(): RotationMode {
        return sharedPref.getString(rotationMode, null)?.let {
            RotationMode.valueOf(it)
        } ?: RotationMode.NONE
    }

    fun setRotationMode(mode: RotationMode) {
        sharedPref.edit {
            putString(rotationMode, mode.name)
        }
    }

    fun setDefineScaleCentered(defined: Boolean) {
        sharedPref.edit {
            putBoolean(defineScaleWhenCentered, defined)
        }
    }

    /**
     * If `true`, [scaleCentered] is accounted for. Otherwise, [scaleCentered] is ignored.
     */
    fun getDefineScaleCentered(): Boolean {
        return sharedPref.getBoolean(defineScaleWhenCentered, true)
    }

    fun setScaleCentered(scale: Float) {
        sharedPref.edit {
            putFloat(scaleCentered, scale)
        }
    }

    /**
     * The scale at which the MapView is set when centering on the current position
     */
    fun getScaleCentered(): Float {
        return sharedPref.getFloat(scaleCentered, 1f)
    }

    fun setFavoriteMapIds(ids: List<Int>) {
        sharedPref.edit {
            putStringSet(favoriteMaps, ids.map { id -> id.toString() }.toSet())
        }
    }

    /**
     * The ids of maps which are marked as favorites.
     */
    fun getFavoriteMapIds(): List<Int> {
        return sharedPref.getStringSet(favoriteMaps, null)?.let {
            it.map { id -> id.toInt() }
        } ?: listOf()
    }

    /**
     * @return The last map id, or null if it's undefined. The returned id is guarantied to be not
     * empty.
     */
    fun getLastMapId(): Int? {
        return sharedPref.getInt(lastMapId, -1).let { id ->
            if (id != -1) id else null
        }
    }

    /**
     * Set and saves the last map id, for further use.
     */
    fun setLastMapId(id: Int) {
        sharedPref.edit {
            putInt(lastMapId, id)
        }
    }
}

/**
 * TODO: Remove this class after migrating settings to shared prefs is done
 *
 * @param appDir The current application directory
 * @param startOnPolicy Whether TrekMe should boot on the map list or on the last map
 * @param defineScaleWhenCenter If `true`, [scaleCentered] is accounted for. Otherwise,
 * [scaleCentered] is ignored.
 * @param scaleCentered The scale at which the MapView is set when centering on the current position
 * @param favoriteMaps The ids of maps which are marked as favorites
 */
@Serializable
private data class SettingsData(val appDir: String,
                                val startOnPolicy: StartOnPolicy = StartOnPolicy.MAP_LIST,
                                val lastMapId: Int = -1,
                                val magnifyingFactor: Int = 0,
                                val rotationMode: RotationMode = RotationMode.NONE,
                                val defineScaleWhenCenter: Boolean = true,
                                val scaleCentered: Float = 1f,
                                val favoriteMaps: List<Int> = listOf())

enum class StartOnPolicy {
    MAP_LIST, LAST_MAP
}

enum class RotationMode {
    NONE, FOLLOW_ORIENTATION, FREE
}

private const val settingsFile = "trekmeSettings"