package com.example.lscontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class MainActivity : Activity() {

    private lateinit var tx: BroadcastController
    private lateinit var badge1: TextView
    private lateinit var badge2: TextView

    private val ch1Btns = mutableListOf<Button>()
    private val ch2Btns = mutableListOf<Button>()
    private var selCh1 = 0
    private var selCh2 = 0

    private var pending: (() -> Unit)? = null
    private lateinit var ai: AiPanel
    private val seqScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var seqJob: Job? = null

    // 青（伸縮）
    private val BL_TEXT   = Color.rgb(0x37, 0x8A, 0xDD)
    private val BL_SEL_BG = Color.rgb(0x1A, 0x2A, 0x3A)
    private val BL_BORDER  = Color.rgb(0x37, 0x8A, 0xDD)

    // ピンク（振動）
    private val PK_TEXT   = Color.rgb(0xD4, 0x53, 0x7E)
    private val PK_SEL_BG = Color.rgb(0x2A, 0x14, 0x20)
    private val PK_BORDER  = Color.rgb(0xD4, 0x53, 0x7E)

    // ニュートラル（黒系）
    private val BG_PAGE  = Color.parseColor("#000000")
    private val BG_CARD  = Color.parseColor("#1C1C1E")
    private val N_BTN    = Color.parseColor("#2C2C2E")
    private val N_BORDER = Color.parseColor("#3A3A3E")
    private val N_TEXT   = Color.parseColor("#FFFFFF")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        tx = BroadcastController(this) { }
        ai = AiPanel(this, { dp(it) }, onCommands = { cmds -> runSequence(cmds) }) {
            withPermission {
                seqJob?.cancel(); seqJob = null
                ai.cancel(); clearSel1(); clearSel2(); tx.stopAll()
            }
        }
        setContentView(buildUi())
    }

    override fun onStop() {
        super.onStop()
        seqJob?.cancel()
        ai.cancel()
        tx.stopAll()
        clearSel1(); clearSel2()
    }

    // ── UI ──────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_PAGE)
            setPadding(dp(14), dp(20), dp(14), dp(28))
        }

        root.addView(ai.build())
        root.addView(gap(dp(10)))

        // 伸縮カード（青）
        badge1 = TextView(this)
        root.addView(channelCard(
            label        = "伸縮",
            accentText   = BL_TEXT,
            accentBg     = BL_SEL_BG,
            accentBorder = BL_BORDER,
            badge        = badge1,
            btns         = ch1Btns,
            onPat        = { n, b -> withPermission { onPat1(n, b) } },
            onStop       = { withPermission { onStop1() } }
        ))

        root.addView(gap(dp(10)))

        // 振動カード（ピンク）
        badge2 = TextView(this)
        root.addView(channelCard(
            label        = "振動",
            accentText   = PK_TEXT,
            accentBg     = PK_SEL_BG,
            accentBorder = PK_BORDER,
            badge        = badge2,
            btns         = ch2Btns,
            onPat        = { n, b -> withPermission { onPat2(n, b) } },
            onStop       = { withPermission { onStop2() } }
        ))

        return ScrollView(this).apply {
            setBackgroundColor(BG_PAGE)
            addView(root)
        }
    }

    private fun channelCard(
        label: String,
        accentText: Int, accentBg: Int, accentBorder: Int,
        badge: TextView,
        btns: MutableList<Button>,
        onPat: (Int, Button) -> Unit,
        onStop: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rr(BG_CARD, dp(12), N_BORDER, dp(1))
        }

        // ヘッダー
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = lp(MATCH_PARENT, WRAP_CONTENT).also { it.bottomMargin = dp(10) }
        }
        val bar = View(this).apply {
            background = rr(accentText, dp(2), Color.TRANSPARENT, 0)
        }
        header.addView(bar, lp(dp(4), dp(22)).also { it.marginEnd = dp(8) })

        val title = TextView(this).apply {
            text = label; textSize = 15f
            setTextColor(accentText); setTypeface(typeface, Typeface.BOLD)
        }
        header.addView(title, lp(0, WRAP_CONTENT, 1f))

        badge.text = "停止中"; badge.textSize = 12f
        badge.setTextColor(accentText)
        badge.setPadding(dp(10), dp(3), dp(10), dp(3))
        badge.background = rr(accentBg, dp(12), Color.TRANSPARENT, 0)
        header.addView(badge, lp(WRAP_CONTENT, WRAP_CONTENT))

        card.addView(header)

        // 3×3 グリッド
        btns.clear()
        for (row in 0..2) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (col in 0..2) {
                val n = row * 3 + col + 1
                val b = Button(this).apply {
                    text = n.toString(); textSize = 20f; isAllCaps = false
                    setTextColor(N_TEXT)
                    background = rr(N_BTN, dp(8), N_BORDER, dp(1))
                    minHeight = dp(60); minWidth = 0
                    setOnClickListener { onPat(n, this) }
                }
                btns.add(b)
                val blp = lp(0, WRAP_CONTENT, 1f)
                if (col < 2) blp.marginEnd = dp(6)
                r.addView(b, blp)
            }
            val rlp = lp(MATCH_PARENT, WRAP_CONTENT)
            if (row < 2) rlp.bottomMargin = dp(6)
            card.addView(r, rlp)
        }

        // チャンネル停止
        card.addView(Button(this).apply {
            text = "$label 停止"; textSize = 13f; isAllCaps = false
            setTextColor(accentText)
            background = rr(accentBg, dp(8), accentBorder, dp(1))
            minHeight = dp(46)
            setOnClickListener { onStop() }
        }, lp(MATCH_PARENT, WRAP_CONTENT).also { it.topMargin = dp(8) })

        return card
    }

    // ── 操作 ──────────────────────────────────────────────────

    private fun onPat1(n: Int, b: Button) {
        seqJob?.cancel(); seqJob = null
        if (selCh1 == n) { onStop1(); return }
        ch1Btns.forEach { resetBtn(it) }
        selCh1 = n; selBtn(b, BL_SEL_BG, BL_TEXT, BL_BORDER)
        badge1.text = "パターン $n"
        tx.sendCh1(BroadcastController.CH1[n])
    }

    private fun onPat2(n: Int, b: Button) {
        seqJob?.cancel(); seqJob = null
        if (selCh2 == n) { onStop2(); return }
        ch2Btns.forEach { resetBtn(it) }
        selCh2 = n; selBtn(b, PK_SEL_BG, PK_TEXT, PK_BORDER)
        badge2.text = "パターン $n"
        tx.sendCh2(BroadcastController.CH2[n])
    }

    private fun onStop1() {
        seqJob?.cancel(); seqJob = null
        clearSel1(); tx.stopCh1()
    }

    private fun onStop2() {
        seqJob?.cancel(); seqJob = null
        clearSel2(); tx.stopCh2()
    }

    private fun clearSel1() {
        ch1Btns.forEach { resetBtn(it) }
        selCh1 = 0; badge1.text = "停止中"
    }

    private fun clearSel2() {
        ch2Btns.forEach { resetBtn(it) }
        selCh2 = 0; badge2.text = "停止中"
    }

    // ── AIシーケンス ──────────────────────────────────────────

    private fun runSequence(cmds: List<AiCommand>) {
        seqJob?.cancel()
        seqJob = seqScope.launch {
            clearSel1(); clearSel2()
            for (cmd in cmds) {
                applyCommand(cmd.channel, cmd.pattern)
                delay(cmd.seconds * 1000L)
            }
            tx.stopAll()
            clearSel1(); clearSel2()
            seqJob = null
        }
    }

    private fun applyCommand(channel: String, pattern: Int) {
        when (channel) {
            "all" -> if (pattern == 0) {
                tx.stopCh1(); tx.stopCh2()
            } else {
                tx.sendCh1(BroadcastController.CH1[pattern])
                tx.sendCh2(BroadcastController.CH2[pattern])
            }
            "stroke" -> {
                if (pattern > 0) tx.sendCh1(BroadcastController.CH1[pattern]) else tx.stopCh1()
                tx.stopCh2()
            }
            "vibe" -> {
                tx.stopCh1()
                if (pattern > 0) tx.sendCh2(BroadcastController.CH2[pattern]) else tx.stopCh2()
            }
        }
    }

    // ── 描画ヘルパー ──────────────────────────────────────────

    private fun selBtn(b: Button, bg: Int, text: Int, border: Int) {
        b.background = rr(bg, dp(8), border, dp(2))
        b.setTextColor(text); b.setTypeface(b.typeface, Typeface.BOLD)
    }

    private fun resetBtn(b: Button) {
        b.background = rr(N_BTN, dp(8), N_BORDER, dp(1))
        b.setTextColor(N_TEXT); b.setTypeface(b.typeface, Typeface.NORMAL)
    }

    private fun rr(fill: Int, r: Int, stroke: Int, sw: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(fill)
            cornerRadius = r.toFloat()
            if (sw > 0) setStroke(sw, stroke)
        }

    private fun lp(w: Int, h: Int, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)

    private fun gap(h: Int) = View(this).apply {
        layoutParams = lp(MATCH_PARENT, h)
        setBackgroundColor(BG_PAGE)
    }

    // ── 権限 ──────────────────────────────────────────────────

    private fun required() =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
        else emptyArray()

    private fun withPermission(action: () -> Unit) {
        val missing = required().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            pending = action
            requestPermissions(missing.toTypedArray(), 1)
            return
        }
        if (!tx.isBluetoothOn) return
        action()
    }

    override fun onRequestPermissionsResult(
        req: Int, perms: Array<out String>, res: IntArray
    ) {
        super.onRequestPermissionsResult(req, perms, res)
        if (res.isNotEmpty() && res.all { it == PackageManager.PERMISSION_GRANTED }) pending?.invoke()
        pending = null
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        ai.onActivityResult(req, res, data)
    }

    override fun onDestroy() {
        seqJob?.cancel()
        seqScope.cancel()
        ai.destroy()
        super.onDestroy()
    }
}
