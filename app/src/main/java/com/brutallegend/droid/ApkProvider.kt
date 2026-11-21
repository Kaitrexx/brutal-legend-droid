package com.brutallegend.droid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Runtime bundle içindəki termux-x11.apk-nın quraşdırılması üçün minimal ContentProvider.
 * FileProvider (AndroidX) əvəzinə öz providerımızı istifadə edirik — layihə AndroidX-sizdir.
 */
class ApkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val name = uri.lastPathSegment ?: return null
        val dir = context?.externalFilesDir ?: return null
        val file = File(dir, name)
        if (!file.exists()) return null
        return AssetFileDescriptor(
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
            0,
            file.length()
        )
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}
