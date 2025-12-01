package com.meq.colourchecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.meq.colourchecker.ui.ColorCheckerApp
import com.meq.colourchecker.ui.theme.ColourCheckerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ColourCheckerTheme {
                ColorCheckerApp()
            }
        }
    }
}
