package com.example.minigames

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.example.minigames.checkers.CheckersView
import com.example.minigames.chess.ChessView
import com.example.minigames.core.BaseView
import com.example.minigames.core.GameId
import com.example.minigames.core.Host
import com.example.minigames.core.I18n
import com.example.minigames.core.Sfx
import com.example.minigames.core.Store
import com.example.minigames.core.Ui
import com.example.minigames.flappy.FlappyView
import com.example.minigames.battleship.BattleshipView
import com.example.minigames.backgammon.BackgammonView
import com.example.minigames.c4.C4View
import com.example.minigames.g2048.Game2048View
import com.example.minigames.hub.HubView
import com.example.minigames.mines.MinesView
import com.example.minigames.sudoku.SudokuView
import com.example.minigames.tictactoe.TttView

/** Single activity: shows one full-screen game view at a time and switches between them. */
class MainActivity : Activity(), Host {

    override lateinit var store: Store
    override lateinit var sfx: Sfx

    private var current: BaseView? = null
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            // draw behind the notch / camera cut-out
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        store = Store(this)
        GameStrings.registerAll()
        I18n.lang = if (store.lang in 0 until I18n.codes.size) store.lang else I18n.detect()
        applyTheme()
        sfx = Sfx(this)
        sfx.enabled = store.soundOn

        show(GameId.HUB)
        hideSystemUi()
    }

    private fun show(id: GameId) {
        current?.stop()
        val view: BaseView = when (id) {
            GameId.HUB -> HubView(this, this)
            GameId.FLAPPY -> FlappyView(this, this)
            GameId.TTT -> TttView(this, this)
            GameId.CHESS -> ChessView(this, this)
            GameId.CHECKERS -> CheckersView(this, this)
            GameId.C4 -> C4View(this, this)
            GameId.BACKGAMMON -> BackgammonView(this, this)
            GameId.G2048 -> Game2048View(this, this)
            GameId.SUDOKU -> SudokuView(this, this)
            GameId.MINES -> MinesView(this, this)
            GameId.BATTLESHIP -> BattleshipView(this, this)
        }
        current = view
        setContentView(view)
        view.requestFocus()
        if (resumed) view.onHostResume()
    }

    // ------------------------------ Host ------------------------------------

    override fun open(id: GameId) {
        // posted so that the view hierarchy is never changed in the middle of a frame callback
        window.decorView.post { show(id) }
    }

    override fun finishApp() {
        finish()
    }

    override fun applyTheme() {
        Ui.dark = when (store.themeMode) {
            0 -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO
            2 -> false
            else -> true
        }
    }

    override fun shareText(text: String) {
        try {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(i, null))
        } catch (e: Exception) {
            // no app can handle it
        }
    }

    override fun updateMusic() {
        // filled in by the music player
    }

    // ------------------------------ lifecycle -------------------------------

    override fun onResume() {
        super.onResume()
        resumed = true
        hideSystemUi()
        current?.onHostResume()
    }

    override fun onPause() {
        resumed = false
        current?.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        current?.stop()
        sfx.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        // Every screen handles Back itself (pause, previous page, back to the home screen);
        // the home screen returns false and the app closes.
        val v = current
        if (v == null || !v.onBack()) super.onBackPressed()
    }

    /** Full-screen immersive mode. */
    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }
}
