package com.devexplorer.core.usecase

import com.devexplorer.core.model.StorageRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ListDirectoryUseCaseTest {

    @Test
    fun sorts_directories_first_then_case_insensitive_name() = runTest {
        val parent = StorageRef.safTree("parent")
        val repo = FakeStorageRepository(
            children = mapOf(
                "parent" to listOf(
                    fileNode("banana.txt"),
                    dirNode("Zebra"),
                    fileNode("Apple.kt"),
                    dirNode("apricot"),
                ),
            ),
        )

        val result = ListDirectoryUseCase(repo).invoke(parent).getOrThrow()

        // Directories first (apricot, Zebra), then files (Apple.kt, banana.txt),
        // each group ordered case-insensitively.
        assertEquals(
            listOf("apricot", "Zebra", "Apple.kt", "banana.txt"),
            result.map { it.name },
        )
    }

    @Test
    fun empty_directory_yields_empty_success() = runTest {
        val repo = FakeStorageRepository(children = mapOf("p" to emptyList()))
        val result = ListDirectoryUseCase(repo).invoke(StorageRef.safTree("p"))
        assertEquals(emptyList<String>(), result.getOrThrow().map { it.name })
    }
}
