package `in`.artham.fundamentals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import `in`.artham.fundamentals.ui.RootNav
import `in`.artham.fundamentals.ui.theme.ArthamTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = artham()
        setContent {
            var dark by remember { mutableStateOf(container.settings.darkTheme) }
            ArthamTheme(dark = dark) {
                RootNav(
                    darkTheme = dark,
                    onToggleTheme = {
                        dark = !dark
                        container.settings.darkTheme = dark
                    }
                )
            }
        }
    }
}
