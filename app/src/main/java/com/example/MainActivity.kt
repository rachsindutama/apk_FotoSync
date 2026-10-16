package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.FotoSyncApp
import com.example.ui.theme.FotoSyncTheme
import com.example.ui.viewmodel.FotoSyncViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FotoSyncTheme {
                val viewModel: FotoSyncViewModel = viewModel()
                FotoSyncApp(viewModel = viewModel)
            }
        }
    }
}
