package io.github.mpridal.jakotata

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mpridal.jakotata.core.settings.AppSettings
import io.github.mpridal.jakotata.ui.MainScreen
import io.github.mpridal.jakotata.ui.theme.JakoTataTheme
import io.github.mpridal.jakotata.ui.theme.ThemeMode

/** AppCompatActivity kvůli přepínání jazyka aplikace přes AppCompatDelegate (zadání kap. 1). */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appSettings = remember { AppSettings(this) }
            val settings by appSettings.flow.collectAsStateWithLifecycle(initialValue = null)
            val s = settings
            // Jazyk aplikace z nastavení (prázdné = podle systému); změna přestaví aktivitu.
            LaunchedEffect(s?.language) {
                val lang = s?.language ?: return@LaunchedEffect
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                if (current != lang) {
                    AppCompatDelegate.setApplicationLocales(
                        if (lang.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang),
                    )
                }
            }
            JakoTataTheme(mode = ThemeMode.entries.getOrElse(s?.theme ?: 0) { ThemeMode.SYSTEM }) {
                MainScreen()
            }
        }
    }
}
