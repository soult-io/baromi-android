package io.soult.baromi.export

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import io.soult.baromi.R
import io.soult.baromi.data.BaromiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Builds the CSV off the main thread, writes it to app cache, and hands a FileProvider uri to the
 * share sheet. Shared by History and Settings. Caller should wrap in try/catch and surface failures.
 */
suspend fun exportAndShareCsv(activity: Activity) {
    val exporter = CheckinExporter(BaromiDatabase.getInstance(activity))
    val uri = withContext(Dispatchers.IO) {
        val dir = File(activity.cacheDir, "exports")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("could not create export directory: $dir")
        }
        val file = File(dir, "baromi-export.csv")
        file.writeText(exporter.exportCsv())
        FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(send, activity.getString(R.string.menu_export)))
}
