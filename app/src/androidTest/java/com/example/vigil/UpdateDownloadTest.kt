package com.example.vigil

import com.example.vigil.update.UpdateChecker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class UpdateDownloadTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val version get() = UpdateChecker.currentVersionName(context)
    private fun digest(body: ByteArray) = "sha256:" + MessageDigest.getInstance("SHA-256")
        .digest(body).joinToString("") { "%02x".format(it) }

    private fun download(body: ByteArray, size: Long = body.size.toLong(), hash: String? = digest(body),
                         requestedVersion: String = version): File? {
        val socket = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val thread = Thread {
            try {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val out = client.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(body)
                    out.flush()
                }
            } catch (_: java.io.IOException) { }
        }
        thread.start()
        return try {
            runBlocking { UpdateChecker.download(context, "http://127.0.0.1:${socket.localPort}/app.apk",
                requestedVersion, size, hash) }
        } finally {
            socket.close()
            thread.join(5000)
        }
    }

    @Test fun validSamePackageAndSignerIsAccepted() {
        val apk = File(context.applicationInfo.sourceDir).readBytes()
        val result = download(apk)
        assertNotNull(result)
        assertEquals(apk.size.toLong(), result!!.length())
        assertTrue(UpdateChecker.isValidUpdateApk(context, result, version))
        result.delete()
    }

    @Test fun corruptBodyIsRejectedDespiteMatchingSizeAndDigest() {
        assertNull(download("this is not an APK".toByteArray()))
    }

    @Test fun wrongAssetSizeAndDigestAreRejected() {
        val apk = File(context.applicationInfo.sourceDir).readBytes()
        assertNull(download(apk, apk.size + 1L))
        assertNull(download(apk, hash = "sha256:" + "0".repeat(64)))
        assertFalse(File(context.cacheDir, "vigil-update-$version.apk.part").exists())
    }

    @Test fun otherPackageAndWrongVersionAreRejected() {
        val testApk = File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir).readBytes()
        assertNull(download(testApk))
        assertNull(download(File(context.applicationInfo.sourceDir).readBytes(), requestedVersion = "999.0.0"))
    }

    @Test fun installationLaunchFailureReturnsFailure() {
        val failingContext = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: android.content.Intent) {
                throw android.content.ActivityNotFoundException("test missing installer")
            }
        }
        val file = File(context.cacheDir, "installation-failure-test.apk")
        file.writeText("fixture")
        try { assertFalse(UpdateChecker.triggerInstall(failingContext, file)) }
        finally { file.delete() }
    }
}
