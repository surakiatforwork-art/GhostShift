package com.phantom.ghostshift.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.MessageDigest

data class AppRelease(val version: String, val url: String, val size: Long, val digest: String?)

object AppUpdater {
    private const val REPO = "surakiatforwork-art/GhostShift"
    private const val MAX_BYTES = 100L * 1024 * 1024
    fun installedVersion(context: Context): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").takeIf { it.matches(Regex("[0-9]+(\\.[0-9]+){0,3}")) }
            ?.split('.')?.map { it.toIntOrNull() ?: return null }
        val r = parts(remote) ?: return false
        val l = parts(local) ?: return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun connection(url: String): HttpsURLConnection = (URL(url).openConnection() as HttpsURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        setRequestProperty("User-Agent", "GhostShift-Updater")
    }

    suspend fun check(context: Context): AppRelease? = withContext(Dispatchers.IO) {
        val conn = connection("https://api.github.com/repos/$REPO/releases/latest")
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) error("GitHub HTTP " + conn.responseCode)
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            if (json.optBoolean("draft") || json.optBoolean("prerelease") || !isNewer(tag, installedVersion(context))) return@withContext null
            val assets = json.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == "app-release.apk" }
                ?: error("ไม่พบไฟล์ APK สำหรับอัปเดต")
            val url = asset.getString("browser_download_url")
            require(url.startsWith("https://github.com/$REPO/releases/download/$tag/")) { "ลิงก์อัปเดตไม่ถูกต้อง" }
            val size = asset.getLong("size")
            require(size in 1..MAX_BYTES) { "ขนาดไฟล์ไม่ถูกต้อง" }
            AppRelease(tag.removePrefix("v"), url, size, asset.optString("digest").takeIf { it.startsWith("sha256:") })
        } finally { conn.disconnect() }
    }

    @Suppress("DEPRECATION")
    suspend fun download(context: Context, release: AppRelease, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val temp = File(dir, "update.part")
        val apk = File(dir, "update.apk")
        temp.delete(); apk.delete()
        val conn = connection(release.url)
        try {
            if (conn.responseCode != 200) error("ดาวน์โหลดไม่สำเร็จ: HTTP " + conn.responseCode)
            val hash = MessageDigest.getInstance("SHA-256")
            var count = 0L
            conn.inputStream.use { input ->
                temp.outputStream().use { out ->
                    val buf = ByteArray(32768)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        count += n
                        require(count <= release.size && count <= MAX_BYTES) { "ขนาด APK ไม่ตรง" }
                        hash.update(buf, 0, n); out.write(buf, 0, n)
                        progress(count.toFloat() / release.size)
                    }
                }
            }
            require(count == release.size) { "ดาวน์โหลดไม่ครบ" }
            val digest = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(release.digest == null || release.digest.equals("sha256:$digest", true)) { "ไฟล์ APK ไม่ตรงกับ SHA-256" }
            val pm = context.packageManager
            val flag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val incoming = pm.getPackageArchiveInfo(temp.absolutePath, flag) ?: error("ไฟล์ APK ไม่ถูกต้อง")
            val installed = pm.getPackageInfo(context.packageName, flag)
            require(incoming.packageName == context.packageName) { "ชื่อแพ็กเกจไม่ตรง" }
            fun code(p: android.content.pm.PackageInfo) = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
            require(code(incoming) > code(installed) && incoming.versionName == release.version) { "เวอร์ชัน APK ไม่ตรง" }
            fun sign(p: android.content.pm.PackageInfo) = (if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures)
                ?.map { it.toCharsString() }?.toSet().orEmpty()
            require(sign(incoming).isNotEmpty() && sign(incoming) == sign(installed)) { "ลายเซ็น APK ไม่ตรงกับแอปที่ติดตั้ง" }
            require(temp.renameTo(apk)) { "บันทึก APK ไม่สำเร็จ" }
            apk
        } finally { conn.disconnect(); temp.delete() }
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}
