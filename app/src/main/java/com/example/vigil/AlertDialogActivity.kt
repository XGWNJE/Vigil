package com.example.vigil

import android.annotation.SuppressLint
// import android.app.KeyguardManager // 如果不用可以移除
// import android.content.Context // 如果不用可以移除
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
        const val EXTRA_MATCHED_KEYWORD = "extra_matched_keyword" // 新增：传递匹配到的关键词
        // 移除了不再需要的 EXTRA_NOTIFICATION_TITLE 和 EXTRA_NOTIFICATION_TEXT
        const val ACTION_ALERT_CONFIRMED = "com.example.vigil.ACTION_ALERT_CONFIRMED"
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "提醒活动已创建 (AlertDialogActivity created)")

        // 设置窗口标志
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // 获取匹配到的关键词
        val matchedKeyword = intent.getStringExtra(EXTRA_MATCHED_KEYWORD)

        // 设置标题（保持不变或简化）
        binding.textViewAlertDialogTitle.text = getString(R.string.alert_dialog_title)

        // 设置简化后的消息
        if (matchedKeyword != null) {
            binding.textViewAlertDialogMessage.text = getString(R.string.alert_dialog_message_keyword_format, matchedKeyword)
        } else {
            // 如果由于某种原因没有传递关键词，显示默认消息
            binding.textViewAlertDialogMessage.text = getString(R.string.alert_dialog_message_default)
            Log.w(TAG, "启动 AlertDialogActivity 时未找到 EXTRA_MATCHED_KEYWORD")
        }

        // 设置新的按钮文本
        binding.buttonAlertDialogConfirm.text = getString(R.string.alert_dialog_confirm_button_long)

        binding.buttonAlertDialogConfirm.setOnClickListener {
            Log.d(TAG, "用户点击了确认按钮，发送广播以停止铃声。")
            val intentBroadcast = Intent(ACTION_ALERT_CONFIRMED)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intentBroadcast)
            finish()
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        Log.d(TAG, "用户尝试按返回键，当前实现为忽略。")
        Toast.makeText(this, "请点击“${getString(R.string.alert_dialog_confirm_button_long)}”按钮以关闭提醒", Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }
}
