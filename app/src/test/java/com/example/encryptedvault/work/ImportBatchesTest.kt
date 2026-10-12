package com.example.encryptedvault.work

import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.SourceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportBatchesTest {
    private fun staged(
        index: Int,
        pathLength: Int = 60,
        nameLength: Int = 20,
        folderLength: Int = 12,
    ) = MediaTransfer.StagedFile(
        path = "/data/user/0/com.example.encryptedvault/cache/import/" + "p".repeat(pathLength) + "$index.jpg",
        info = SourceInfo("n".repeat(nameLength) + "$index.jpg", "f".repeat(folderLength), index.toLong()),
    )

    @Test fun smallSelectionIsOneBatch() {
        val files = (1..5).map { staged(it) }
        assertEquals(listOf(files), ImportWorker.batchesOf(files))
    }

    @Test fun emptySelectionHasNoBatches() {
        assertTrue(ImportWorker.batchesOf(emptyList()).isEmpty())
    }

    @Test fun largeSelectionWithLongNamesSplitsIntoBatchesThatBuild() {
        // 400 files of ~300 bytes of path, name and folder each: one input would be well over WorkManager's 10 KB limit.
        val files = (1..400).map { staged(it, pathLength = 200, nameLength = 80, folderLength = 80) }
        val batches = ImportWorker.batchesOf(files)
        assertTrue("split into several batches", batches.size > 1)
        batches.forEach { batch -> ImportWorker.inputFor(batch) } // throws if a batch is still over the limit
        assertEquals("no file is lost or reordered", files, batches.flatten())
    }

    @Test fun manySmallFilesStillFitInFewBatches() {
        val files = (1..150).map { staged(it) }
        val batches = ImportWorker.batchesOf(files)
        assertTrue("batches are reasonably full", batches.size < files.size)
        assertEquals(files, batches.flatten())
    }
}
