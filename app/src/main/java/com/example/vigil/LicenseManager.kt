// src/main/java/com/example/vigil/LicenseManager.kt
package com.example.vigil

import android.util.Base64 // 导入 Base64 工具类
import android.util.Log
import org.json.JSONObject // 导入 JSONObject 用于 JSON 解析
import java.lang.IllegalArgumentException // 导入处理 Base64 解码错误的异常
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.* // 导入 Date 和 Calendar 用于时间戳处理

/**
 * 数据类，表示授权信息的核心内容。
 * 对应生成器中的 LicensePayload。
 */
data class LicensePayload(
    val appId: String,            // 应用ID，通常是应用的包名
    val licenseType: String,      // 授权类型，例如 "premium", "trial", "pro"
    val issuedAt: Long,           // 授权签发时间戳 (UTC 秒级)
    val expiresAt: Long? = null,  // 授权到期时间戳 (UTC 秒级)，null 表示永不过期
    val features: List<String>? = null // (可选) 此授权解锁的特定功能列表
)

/**
 * LicenseManager 用于处理应用的授权验证逻辑。
 * 在第三阶段，它负责解析、解码、签名验证和 JSON 解析。
 */
class LicenseManager {

    companion object {
        private const val TAG = "LicenseManager"
        private const val LICENSE_SEPARATOR = "." // 授权码信息和签名的分隔符
        private const val HMAC_SHA256 = "HmacSHA256"

        // TODO: !!! 重要 !!!
        // 这是用于客户端验证的密钥。在实际应用中，这个密钥绝对不能明文存储！
        // 您需要采用密钥拆分、混淆、NDK 等方式来保护这个密钥。
        // 为了演示，这里使用一个硬编码的占位符，请务必替换并保护好您的实际密钥。
        private const val CLIENT_VERIFICATION_SECRET_KEY = "A_Very_Unique_And_Secret_Key_For_Vigil_Generator_App_Only_123!@#"    }

    /**
     * 解析、解码并验证授权码字符串。
     *
     * @param licenseKey 用户输入的原始授权码字符串。
     * @param currentAppId 当前应用的包名，用于校验 appId。
     * @return 如果授权码有效且未过期，返回解析后的 LicensePayload 对象。
     * 如果授权码无效、格式错误、签名不匹配、appId 不匹配或已过期，返回 null。
     */
    fun verifyLicense(licenseKey: String, currentAppId: String): LicensePayload? {
        Log.d(TAG, "尝试验证授权码: $licenseKey")

        // 1. 解析并解码
        val decodedParts = decodeLicenseKey(licenseKey)
        if (decodedParts == null) {
            Log.w(TAG, "授权码解析或解码失败。")
            return null
        }
        val (decodedPayloadString, receivedSignatureBytes) = decodedParts

        // 2. 重新计算签名
        val calculatedSignatureBytes = calculateHmacSha256(decodedPayloadString, CLIENT_VERIFICATION_SECRET_KEY)
        if (calculatedSignatureBytes == null) {
            Log.e(TAG, "计算签名失败。")
            return null
        }

        // 3. 比较签名
        if (!MessageDigest.isEqual(receivedSignatureBytes, calculatedSignatureBytes)) {
            Log.w(TAG, "签名验证失败：重新计算的签名与接收到的签名不匹配。")
            return null
        }
        Log.d(TAG, "签名验证成功。")

        // 4. 解析 JSON Payload
        val payloadJson: JSONObject
        try {
            payloadJson = JSONObject(decodedPayloadString)
            Log.d(TAG, "JSON Payload 解析成功。")
        } catch (e: Exception) {
            Log.e(TAG, "JSON Payload 解析失败: ${e.message}")
            return null
        }

        // 5. 校验授权信息内容
        try {
            val appId = payloadJson.getString("appId")
            val licenseType = payloadJson.getString("licenseType")
            val issuedAt = payloadJson.getLong("issuedAt")
            val expiresAt = if (payloadJson.has("expiresAt")) payloadJson.getLong("expiresAt") else null
            val features = if (payloadJson.has("features")) {
                val featuresArray = payloadJson.getJSONArray("features")
                val featureList = mutableListOf<String>()
                for (i in 0 until featuresArray.length()) {
                    featureList.add(featuresArray.getString(i))
                }
                featureList
            } else null

            // 校验 appId
            if (appId != currentAppId) {
                Log.w(TAG, "App ID 不匹配。授权码可能不是为当前应用生成的。")
                return null
            }
            Log.d(TAG, "App ID 校验成功。")

            // 校验过期时间
            if (expiresAt != null) {
                val currentTimeSeconds = System.currentTimeMillis() / 1000
                if (currentTimeSeconds > expiresAt) {
                    Log.w(TAG, "授权已过期。")
                    return null
                }
                Log.d(TAG, "授权有效期校验成功（未过期）。")
            } else {
                Log.d(TAG, "授权无过期时间。")
            }

            // 构建 LicensePayload 对象
            val licensePayload = LicensePayload(appId, licenseType, issuedAt, expiresAt, features)
            Log.i(TAG, "授权码验证成功，授权信息: $licensePayload")
            return licensePayload

        } catch (e: Exception) {
            Log.e(TAG, "校验授权信息内容时出错: ${e.message}")
            return null
        }
    }

    /**
     * 使用 HMAC-SHA256 算法计算签名。
     *
     * @param data 要签名的数据字符串。
     * @param key 用于签名的密钥字符串。
     * @return 计算出的签名字节数组，如果发生错误则返回 null。
     */
    private fun calculateHmacSha256(data: String, key: String): ByteArray? {
        return try {
            val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_SHA256)
            val mac = Mac.getInstance(HMAC_SHA256)
            mac.init(secretKeySpec)
            mac.doFinal(data.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "HMAC-SHA256 计算失败", e)
            null
        }
    }

    /**
     * 解析并解码授权码字符串。（此方法已在 verifyLicense 中调用，并包含初步格式检查）
     * 保留此方法以保持职责分离，但其主要调用点是 verifyLicense。
     *
     * @param licenseKey 用户输入的原始授权码字符串。
     * @return 如果格式正确且 Base64 解码成功，返回一个 Pair，first 是解码后的授权信息字符串，second 是解码后的签名字节数组。
     * 如果格式不正确或 Base64 解码失败，返回 null。
     */
    fun decodeLicenseKey(licenseKey: String): Pair<String, ByteArray>? {
        Log.d(TAG, "尝试解析并解码授权码 (由 verifyLicense 调用): $licenseKey")
        val parts = licenseKey.split(LICENSE_SEPARATOR)

        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            Log.w(TAG, "授权码格式无效（分隔符或空部分）。")
            return null
        }

        val base64EncodedPayload = parts[0]
        val base64EncodedSignature = parts[1]

        try {
            // Base64 解码授权信息部分
            val decodedPayloadBytes = Base64.decode(base64EncodedPayload, Base64.DEFAULT)
            val decodedPayloadString = String(decodedPayloadBytes, Charsets.UTF_8) // 假设是 UTF-8 编码的 JSON

            // 初步验证解码后的 Payload 是否像 JSON
            if (!decodedPayloadString.trimStart().startsWith("{") || !decodedPayloadString.trimEnd().endsWith("}")) {
                Log.w(TAG, "解码后的授权信息不像 JSON 格式。")
                // 这里可以选择返回 null 或继续，取决于验证严格程度
                // 为了阶段调试，我们先允许继续，但在后续阶段会严格校验 JSON
            }
            Log.d(TAG, "Base64 解码 Payload 成功。")
            // Log.d(TAG, "解码后的 Payload: $decodedPayloadString") // 解码后的 Payload 可能包含敏感信息，调试时谨慎打印

            // Base64 解码签名部分
            val decodedSignatureBytes = Base64.decode(base64EncodedSignature, Base64.DEFAULT)
            Log.d(TAG, "Base64 解码 Signature 成功。")

            return Pair(decodedPayloadString, decodedSignatureBytes)

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Base64 解码失败: ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "解码过程中发生未知错误: ${e.message}")
            return null
        }
    }

    // TODO: 在后续阶段添加保存授权状态到 SharedPreferences 的方法
}
