package com.tend.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tend.app.ui.TendApp
import com.tend.app.ui.theme.TendTheme

/** Deep links handed from notifications to the (single) compose shell. */
object DeepLinks {
    @Volatile
    var pending: String? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        captureDeepLink(intent)
        setContent {
            TendTheme {
                TendApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: Intent?) {
        intent?.getStringExtra(EXTRA_DEEPLINK)?.let { DeepLinks.pending = it }
    }

    companion object {
        const val EXTRA_DEEPLINK = "tend_deeplink"
        const val DEEPLINK_PLAN_TOMORROW = "plan_tomorrow"
    }
}
