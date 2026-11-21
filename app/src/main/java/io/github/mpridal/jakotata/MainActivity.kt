package io.github.mpridal.jakotata

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.github.mpridal.jakotata.ui.MainScreen
import io.github.mpridal.jakotata.ui.theme.JakoTataTheme

/** AppCompatActivity kvůli přepínání jazyka aplikace přes AppCompatDelegate (zadání kap. 1). */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JakoTataTheme {
                MainScreen()
            }
        }
    }
}
