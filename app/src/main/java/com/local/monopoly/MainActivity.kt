package com.local.monopoly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.local.monopoly.game.Engine
import com.local.monopoly.game.SaveStore
import com.local.monopoly.ui.GameScreen
import com.local.monopoly.ui.SetupScreen
import com.local.monopoly.ui.theme.MonopolyTheme

/**
 * Single-activity app, portrait only. No permissions, no internet,
 * no images, no audio — the whole game is code-drawn Compose UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonopolyTheme {
                val ctx = LocalContext.current
                val engine = remember { Engine(SaveStore(ctx)) }
                if (engine.state == null) SetupScreen(engine) else GameScreen(engine)
            }
        }
    }
}
