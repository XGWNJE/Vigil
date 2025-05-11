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
    private var isConfirmButtonClicked: Boolean = false // 新增标志位

    companion object {
        const val TAG = "AlertDialogActivity"
        const val EXTRA_MATCHED_KEYWORD = "extra_matched_keyword"
        const val ACTION_ALERT_CONFIRMED = "com.example.vigil.ACTION_ALERT_CONFIRMED"
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "提醒活动已创建 (AlertDialogActivity created)")
        isConfirmButtonClicked = false // 初始化标志位

        // 设置窗口标志以在锁屏时显示和点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
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
            isConfirmButtonClicked = true // 设置标志位
            sendStopSignal()
            finish()
        }
    }

    private fun sendStopSignal() {
        Log.d(TAG, "发送 ACTION_ALERT_CONFIRMED 广播。")
        val intentBroadcast = Intent(ACTION_ALERT_CONFIRMED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intentBroadcast)
    }

    @SuppressLint("MissingSuperCall")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        Log.d(TAG, "用户尝试按返回键，当前实现为忽略。")
        Toast.makeText(this, "请点击“${getString(R.string.alert_dialog_confirm_button_long)}”按钮以关闭提醒", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AlertDialogActivity onDestroy. isConfirmButtonClicked: $isConfirmButtonClicked")
        if (!isConfirmButtonClicked) {
            // 如果不是通过点击确认按钮关闭的（例如系统或其他原因销毁了Activity）
            // 也发送停止信号，以确保铃声和唤醒锁被处理
            Log.i(TAG, "Activity 因其他原因销毁，发送停止信号。")
            sendStopSignal()
        }
    }
}
