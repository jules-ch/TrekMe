package com.peterlaurence.trekme.core

import android.content.Context
import android.os.Build.VERSION_CODES.Q
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * General context attributes of the application.
 * Here are defined :
 *
 *  * The default root folder of the application on the external storage
 *  * Where maps are searched
 *  * Where maps can be downloaded
 *  * The default folder in which new maps downloaded from the internet are imported
 *  * The folder where credentials are stored
 *  * The folder where recordings are saved
 *  * The file in which app settings are saved (it's a private folder)
 *
 * @author peterLaurence on 07/10/17 -- converted to Kotlin on 20/11/18
 */
interface TrekMeContext {
    var defaultAppDir: File?
    val defaultMapsDownloadDir: File?
    val importedDir: File?
    val recordingsDir: File?
    var mapsDirList: List<File>?
    val credentialsDir: File
    val isAppDirReadOnly: Boolean
    fun init(applicationContext: Context)
    @Deprecated("Will be removed after migrating settings to shared prefs is done")
    fun getSettingsFile(applicationContext: Context): File
    fun checkAppDir(): Boolean
}

class TrekMeContextAndroid : TrekMeContext {
    override var defaultAppDir: File? = null

    override val defaultMapsDownloadDir: File? by lazy {
        defaultAppDir?.let {
            File(it, "downloaded")
        }
    }

    /* Where zip archives are extracted */
    override val importedDir: File? by lazy {
        defaultAppDir?.let {
            File(it, "imported")
        }
    }

    override val recordingsDir: File? by lazy {
        defaultAppDir?.let {
            File(it, "recordings")
        }
    }

    /* Where maps are searched */
    override var mapsDirList: List<File>? = null

    /* Where maps can be downloaded */
    val downloadDirList: List<File>? by lazy {
        mapsDirList?.map {
            File(it, "downloaded")
        }
    }

    private val TAG = "TrekMeContextAndroid"

    override val credentialsDir: File by lazy {
        File(defaultAppDir, "credentials")
    }

    /**
     * Check whether the app root dir is in read-only state or not. This is usually used only if the
     * [checkAppDir] call returned `false`
     */
    override val isAppDirReadOnly: Boolean
        get() = Environment.getExternalStorageState(defaultAppDir) == Environment.MEDIA_MOUNTED_READ_ONLY

    /**
     * Create necessary folders and files, and identify folder in which the maps will be searched
     * into.
     * @param applicationContext The context that *should not* be an Activity context. It should be
     * obtained from [Context.getApplicationContext].
     */
    override fun init(applicationContext: Context) {
        try {
            resolveDirs(applicationContext)
            createAppDirs()
            createNomediaFile()
        } catch (e: SecurityException) {
            Log.e(TAG, "We don't have right access to create application folder")
        } catch (e: IOException) {
            Log.e(TAG, "We don't have right access to create application folder")
        }
    }

    /**
     * The settings file is stored in a private folder of the app, and this folder will be deleted
     * if the app is uninstalled. This is intended, not to persist those settings.
     * TODO: Remove this method after migrating settings to shared prefs is done
     */
    override fun getSettingsFile(applicationContext: Context): File {
        return File(applicationContext.filesDir, "settings.json")
    }

    /**
     * We distinguish two cases:
     * * Android < 10: We use the "trekme" folder in the internal memory as the default app dir.
     * Using [Context.getExternalFilesDirs], we indirectly the directory of the SD card (if there
     * is one). The first [File] returned returned by that last api is a folder on the internal
     * memory whose files are removed when the app is uninstalled. This isn't the original behavior
     * if TrekMe so we don't use it on Android 9 and below.
     * * Android >= 10: We no longer use the "trekme" folder. Scoped storage imposes that the [File]
     * api can only be used within files returned by [Context.getExternalFilesDirs] - files that are
     * private to the app, either on the internal memory or on a SD card. So on Android 10 and above,
     * maps are deleted upon app uninstall. To circle around this issue, the map save & restore
     * features have been redesigned so that the user has more control on where maps are saved and
     * from where to restore.
     */
    private fun resolveDirs(applicationContext: Context) {
        val dirs: List<File> = applicationContext.getExternalFilesDirs(null).filterNotNull()

        if (android.os.Build.VERSION.SDK_INT >= Q) {
            defaultAppDir = dirs.firstOrNull()
            mapsDirList = dirs
        } else {
            defaultAppDir = File(Environment.getExternalStorageDirectory(), appFolderName)
            val otherDirs = dirs.drop(1)
            mapsDirList = listOf(defaultAppDir!!) + otherDirs
        }
    }


    /**
     * To function properly, the app needs to have read + write access to its root directory
     */
    override fun checkAppDir(): Boolean {
        return Environment.getExternalStorageState(defaultAppDir) == Environment.MEDIA_MOUNTED
    }

    @Throws(SecurityException::class)
    private fun createAppDirs() {
        /* Root: try to import legacy first */
        renameLegacyDir()
        createDir(defaultAppDir, "application")

        /* Credentials */
        createDir(credentialsDir, "credentials")

        /* Downloads */
        createDir(defaultMapsDownloadDir, "downloads")

        /* Recordings */
        createDir(recordingsDir, "recordings")
    }

    /**
     * If we detect the existence of the legacy dir, rename it.
     * Only do this for Android version under 10, since the former default app dir was obtained with
     * a now deprecated call. People with Android 10 or new are very unlikely to have installed
     * TrekAdvisor anyway.
     */
    private fun renameLegacyDir() {
        if (android.os.Build.VERSION.SDK_INT < Q) {
            val legacyAppDir = File(Environment.getExternalStorageDirectory(),
                    appFolderNameLegacy)
            if (legacyAppDir.exists()) {
                val defaultAppDir = defaultAppDir
                if (defaultAppDir != null) {
                    legacyAppDir.renameTo(defaultAppDir)
                }
            }
        }
    }

    private fun createDir(dir: File?, label: String) {
        if (dir != null && !dir.exists()) {
            val created = dir.mkdir()
            if (!created) {
                Log.e(TAG, "Could not create $label folder")
            }
        }
    }

    /**
     * We have to create an empty ".nomedia" file at the root of each folder where TrekMe can
     * download maps. This way, other apps don't index this content for media files.
     */
    @Throws(SecurityException::class, IOException::class)
    private fun createNomediaFile() {
        mapsDirList?.forEach {
            if (it.exists()) {
                val noMedia = File(defaultAppDir, ".nomedia")
                val created = noMedia.createNewFile()
                if (!created) {
                    Log.e(TAG, "Could not create .nomedia file")
                }
            }
        }
    }
}

private const val appFolderName = "trekme"
private const val appFolderNameLegacy = "trekadvisor"
const val appName = "TrekMe"
