package com.garminaiexporter

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

class ExportStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("storage", Context.MODE_PRIVATE)

    fun savedTree(): Uri? = prefs.getString("tree_uri", null)?.let(Uri::parse)

    fun rememberTree(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, IntentFlags.READ_WRITE)
        prefs.edit().putString("tree_uri", uri.toString()).apply()
    }

    fun write(tree: Uri, requestedName: String, content: String): String {
        val resolver = context.contentResolver
        val name = uniqueName(resolver, tree, requestedName)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val document = DocumentsContract.createDocument(resolver, root, "text/markdown", name)
            ?: error("Android did not create the export file")
        resolver.openOutputStream(document, "w")?.bufferedWriter(Charsets.UTF_8).use { writer ->
            requireNotNull(writer) { "Cannot open the selected folder for writing" }
            writer.write(content)
        }
        return name
    }

    private fun uniqueName(resolver: ContentResolver, tree: Uri, requested: String): String {
        val dot = requested.lastIndexOf('.')
        val stem = if (dot > 0) requested.substring(0, dot) else requested
        val extension = if (dot > 0) requested.substring(dot) else ""
        val existing = mutableSetOf<String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) existing += cursor.getString(0)
        }
        if (requested !in existing) return requested
        var suffix = 2
        while ("${stem}_$suffix$extension" in existing) suffix++
        return "${stem}_$suffix$extension"
    }

    private object IntentFlags {
        const val READ_WRITE = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
