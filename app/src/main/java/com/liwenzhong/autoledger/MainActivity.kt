package com.liwenzhong.autoledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.liwenzhong.autoledger.ui.AutoLedgerApp
import com.liwenzhong.autoledger.ui.AutoLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoLedgerTheme {
                AutoLedgerApp()
            }
        }
    }
}
