package com.research.healthconnectplus.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.research.healthconnectplus.data.HCPlusDatabase
import com.research.healthconnectplus.data.HeartDAO
import com.research.healthconnectplus.data.StepDAO


class HCPlusContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.research.healthconnectplus.provider"
        private const val STEPS_DIR = 1
        private const val HEART_DIR = 2
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "step_records", STEPS_DIR)
            addURI(AUTHORITY, "heart_records", HEART_DIR)
        }
    }

    private lateinit var dbHCPlus: HCPlusDatabase
    private lateinit var stepDAO: StepDAO
    private lateinit var heartDAO: HeartDAO

    override fun onCreate(): Boolean {
        Log.d("HCPlusContentProvider", "onCreate")
        dbHCPlus = HCPlusDatabase.getDatabase(context!!)
        stepDAO = dbHCPlus.stepDAO()
        heartDAO = dbHCPlus.heartDAO()

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {

        return when (MATCHER.match(uri)) {
            STEPS_DIR -> {
                val cursor = stepDAO.fetchCursor()
                cursor.setNotificationUri(context?.contentResolver, uri)
                cursor

            }

            HEART_DIR -> {
                val cursor = heartDAO.fetchCursor()
                cursor.setNotificationUri(context?.contentResolver, uri)
                cursor
            }

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }

    }

    override fun getType(uri: Uri): String {
        return when (MATCHER.match(uri)) {
            STEPS_DIR -> "vnd.android.cursor.dir/$AUTHORITY.step_records"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun insert(p0: Uri, p1: ContentValues?): Uri? {
        return null
    }

    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
        return 0
    }

    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
        return 0
    }
}
