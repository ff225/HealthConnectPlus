package com.research.healthconnectplus.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.research.healthconnectplus.data.HCPlusDatabase
import com.research.healthconnectplus.data.HeartDAO
import com.research.healthconnectplus.data.MovesenseDAO
import com.research.healthconnectplus.data.StepDAO

class HCPlusContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.research.healthconnectplus.provider"
        private const val STEPS_DIR = 1
        private const val HEART_DIR = 2
        private const val MOVESENSE_DIR = 3

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "step_records", STEPS_DIR)
            addURI(AUTHORITY, "heart_records", HEART_DIR)
            addURI(AUTHORITY, "movesense_records", MOVESENSE_DIR)
        }
    }

    private lateinit var dbHCPlus: HCPlusDatabase
    private lateinit var stepDAO: StepDAO
    private lateinit var heartDAO: HeartDAO
    private lateinit var movesenseDAO: MovesenseDAO

    override fun onCreate(): Boolean {
        Log.d("HCPlusContentProvider", "onCreate")
        dbHCPlus = HCPlusDatabase.getDatabase(context!!)
        stepDAO = dbHCPlus.stepDAO()
        heartDAO = dbHCPlus.heartDAO()
        movesenseDAO = dbHCPlus.movesenseDAO()

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
                // 🆕 Controllo se sortOrder contiene LIMIT per benchmark
                val cursor = if (sortOrder?.startsWith("LIMIT", ignoreCase = true) == true) {
                    val limitValue = extractLimitValue(sortOrder)
                    Log.d("HCPlusContentProvider", "Using LIMIT $limitValue for steps query")
                    stepDAO.fetchCursorWithLimit(limitValue)
                } else {
                    Log.d("HCPlusContentProvider", "Using full steps query (no limit)")
                    stepDAO.fetchCursor()
                }
                cursor.setNotificationUri(context?.contentResolver, uri)
                cursor
            }

            HEART_DIR -> {
                // Per ora solo supporto normale per heart rate
                val cursor = heartDAO.fetchCursor()
                cursor.setNotificationUri(context?.contentResolver, uri)
                cursor
            }

            MOVESENSE_DIR -> {
                // Per ora solo supporto normale per movesense
                val cursor = movesenseDAO.fetchCursor()
                cursor.setNotificationUri(context?.contentResolver, uri)
                cursor
            }

            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    /**
     * Estrae il valore LIMIT dal sortOrder string
     * Esempi: "LIMIT 1000" -> 1000, "LIMIT   5000  " -> 5000
     */
    private fun extractLimitValue(sortOrder: String): Int {
        return try {
            sortOrder
                .substringAfter("LIMIT", "")
                .trim()
                .toIntOrNull() ?: Int.MAX_VALUE
        } catch (e: Exception) {
            Log.w("HCPlusContentProvider", "Failed to parse LIMIT from sortOrder: $sortOrder", e)
            Int.MAX_VALUE
        }
    }

    override fun getType(uri: Uri): String {
        return when (MATCHER.match(uri)) {
            STEPS_DIR -> "vnd.android.cursor.dir/$AUTHORITY.step_records"
            HEART_DIR -> "vnd.android.cursor.dir/$AUTHORITY.heart_records"
            MOVESENSE_DIR -> "vnd.android.cursor.dir/$AUTHORITY.movesense_records"
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