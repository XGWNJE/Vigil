// src/main/java/com/example/vigil/LicenseManager.kt
package com.example.vigil

import android.util.Base64 // 导入 Base64 工具类
import android.util.Log
import java.lang.IllegalArgumentException // 导入处理 Base64 解码错误的异常

/**
 * LicenseManager 用于处理应用的授权验证逻辑。
 * 在第二阶段，它负责解析授权码字符串并进行 Base64 解码。
 */
class LicenseManager {

    companion object {
        private const val TAG = "LicenseManager"
        private const val LICENSE_SEPARATOR = "." // 授权码信息和签名的分隔符
    }

    /**
     * 解析并解码授权码字符串。
     *
     * @param licenseKey 用户输入的原始授权码字符串。
     * @return 如果格式正确且 Base64 解码成功，返回一个 Pair，first 是解码后的授权信息字符串，second 是解码后的签名字节数组。
     * 如果格式不正确或 Base64 解码失败，返回 null。
     */
    fun decodeLicenseKey(licenseKey: String): Pair<String, ByteArray>? {
        Log.d(TAG, "尝试解析并解码授权码: $licenseKey")
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

    // TODO: 在后续阶段添加验证签名、校验授权信息、保存状态等方法
}
