package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNodeTest {

    private fun file(name: String, mime: String? = null) =
        FileNode(StorageRef.safDocument(name), name, isDirectory = false, mimeType = mime)

    private fun dir(name: String) =
        FileNode(StorageRef.safTree(name), name, isDirectory = true)

    @Test
    fun directory_is_categorized_as_directory() {
        assertEquals(FileCategory.Directory, dir("lib").category)
    }

    @Test
    fun apk_detected_by_extension() {
        assertEquals(FileCategory.Apk, file("app-release.apk").category)
    }

    @Test
    fun apk_detected_by_mime_even_without_extension() {
        assertEquals(FileCategory.Apk, file("bundle", mime = FileNode.APK_MIME).category)
    }

    @Test
    fun archive_detected_by_extension() {
        assertEquals(FileCategory.Archive, file("sources.zip").category)
    }

    @Test
    fun text_detected_by_extension_case_insensitive() {
        assertEquals(FileCategory.Text, file("MainActivity.KT").category)
    }

    @Test
    fun unknown_extension_is_other() {
        assertEquals(FileCategory.Other, file("photo.jpeg").category)
    }
}
