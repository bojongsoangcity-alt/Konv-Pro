package com.dooyappwork.konveksipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.dooyappwork.konveksipro.core.navigation.KonveksiNavGraph
import com.dooyappwork.konveksipro.core.theme.KonveksiProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KonveksiProTheme {
                val navController = rememberNavController()
                KonveksiNavGraph(navController = navController)
            }
        }
    }
}
