package com.slideindex.app.ocr

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OcrModelHttpDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: OcrModelHttpDownloader
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = OcrModelHttpDownloader(OkHttpClient())
        outputDir = Files.createTempDirectory("ocr-http-downloader").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        outputDir.deleteRecursively()
    }

    @Test
    fun downloadFile_resumesPartialWithRange() = runBlocking {
        val output = File(outputDir, "resume.yml")
        output.writeBytes("hel".toByteArray())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setBody("lo")
                .addHeader("Content-Range", "bytes 3-4/5"),
        )

        downloader.downloadFile(
            url = server.url("/resume.yml").toString(),
            output = output,
            relativePath = "rec/inference.yml",
            onProgress = {},
        )

        assertArrayEquals("hello".toByteArray(), output.readBytes())
    }

    @Test
    fun downloadFileWithFallback_usesSecondMirrorAfterFirstFailure() = runBlocking {
        val output = File(outputDir, "fallback.yml")
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("ok"))

        downloader.downloadFileWithFallback(
            urls = listOf(
                server.url("/fail").toString(),
                server.url("/ok").toString(),
            ),
            output = output,
            relativePath = "rec/inference.yml",
            onProgress = {},
        )

        assertEquals("ok", output.readText())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun downloadFileWithFallback_throwsWhenAllMirrorsFail() = runBlocking {
        val output = File(outputDir, "all-fail.yml")
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(503))

        val error = runCatching {
            downloader.downloadFileWithFallback(
                urls = listOf(
                    server.url("/a").toString(),
                    server.url("/b").toString(),
                ),
                output = output,
                relativePath = "rec/inference.yml",
                onProgress = {},
            )
        }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        assertTrue(error?.message?.contains("download_failed") == true)
    }
}
