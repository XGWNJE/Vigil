// src/main/java/com/example/vigil/AlertDialogActivity.kt
package com.example.vigil

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.vigil.databinding.ActivityAlertDialogBinding

class AlertDialogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertDialogBinding
    companion object {
        const val TAG = "AlertDialogActivity"
        const val EXTRA_MATCHED_KEYWORD = "extra_matched_keyword"
        const val ACTION_ALERT_CONFIRMED = "com.example.vigil.ACTION_ALERT_CONFIRMED"
    }

    @Suppress("DEPRECATION") // 用于 window.addFlags 和下面的 onBackPressed
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "提醒活动已创建 (AlertDialogActivity created)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            // keyguardManager.requestDismissKeyguard(this, null) // 可选：尝试解锁屏幕
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or // 尝试解锁
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val matchedKeyword = intent.getStringExtra(EXTRA_MATCHED_KEYWORD)

        binding.textViewAlertDialogTitle.text = getString(R.string.alert_dialog_title)

        if (matchedKeyword != null) {
            binding.textViewAlertDialogMessage.text = getString(R.string.alert_dialog_message_keyword_format, matchedKeyword)
        } else {
            binding.textViewAlertDialogMessage.text = getString(R.string.alert_dialog_message_default)
            Log.w(TAG, "启动 AlertDialogActivity 时未找到 EXTRA_MATCHED_KEYWORD")
        }

        binding.buttonAlertDialogConfirm.text = getString(R.string.alert_dialog_confirm_button_long)

        binding.buttonAlertDialogConfirm.setOnClickListener {
            Log.d(TAG, "用户点击了确认按钮，发送广播以停止铃声。")
            val intentBroadcast = Intent(ACTION_ALERT_CONFIRMED)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intentBroadcast)
            finish()
        }
    }

    @SuppressLint("MissingSuperCall") // 我们有意覆盖返回键行为
    @Suppress("DEPRECATION") // 标记此方法覆盖了一个废弃成员
    override fun onBackPressed() {
        // 阻止用户通过返回键关闭此对话框，强制其点击确认按钮
        Log.d(TAG, "用户尝试按返回键，当前实现为忽略。")
        Toast.makeText(this, "请点击“${getString(R.string.alert_dialog_confirm_button_long)}”按钮以关闭提醒", Toast.LENGTH_SHORT).show()
        // 不调用 super.onBackPressed()
    }

    @Suppress("DEPRECATION") // 用于 window.addFlags
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 确保即使在旧版本上，窗口也能在锁屏时显示
        // 这些标志在 onCreate 中也已设置，此处为双重保障或特定时机需要
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }
}
