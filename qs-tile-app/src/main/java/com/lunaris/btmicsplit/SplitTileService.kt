package com.lunaris.btmicsplit

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.io.BufferedReader
import java.io.InputStreamReader

class SplitTileService : TileService() {

    companion object {
        private const val PROP = "persist.lunaris.btmicsplit"
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val enabling = !readProp()
        if (runAsRoot("setprop $PROP ${if (enabling) "1" else "0"}")) {
            refreshTile()
        }
        // If runAsRoot failed (root grant denied/unavailable), the tile just
        // stays in its previous state — nothing silently half-applies.
    }

    private fun refreshTile() {
        val on = readProp()
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "BT out / phone mic"
            subtitle = if (on) "On" else "Off"
            updateTile()
        }
    }

    private fun readProp(): Boolean {
        return runAsRootCapture("getprop $PROP").trim() == "1"
    }

    /** Runs a command as root, discarding output, returns whether it exited 0. */
    private fun runAsRoot(cmd: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor() == 0
        } catch (t: Throwable) {
            false
        }
    }

    /** Runs a command as root and returns stdout. */
    private fun runAsRootCapture(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor()
            out
        } catch (t: Throwable) {
            ""
        }
    }
}
