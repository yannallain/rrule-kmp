package io.github.yallain.rrule.integration.shrinker

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.github.yallain.rrule.integration.shrinker.ShrinkerSmokeContract.ERROR_KEY
import io.github.yallain.rrule.integration.shrinker.ShrinkerSmokeContract.STATUS_FAILED
import io.github.yallain.rrule.integration.shrinker.ShrinkerSmokeContract.STATUS_KEY
import io.github.yallain.rrule.integration.shrinker.ShrinkerSmokeContract.STATUS_METHOD
import io.github.yallain.rrule.integration.shrinker.ShrinkerSmokeContract.STATUS_OK

class ShrinkerSmokeStatusProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == STATUS_METHOD) { "Unsupported method: $method" }

        val result = runCatching(ShrinkerSmokeVerifier::verify)
        return Bundle().apply {
            putString(
                STATUS_KEY,
                if (result.isSuccess) STATUS_OK else STATUS_FAILED,
            )
            putString(ERROR_KEY, result.exceptionOrNull()?.stackTraceToString())
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
