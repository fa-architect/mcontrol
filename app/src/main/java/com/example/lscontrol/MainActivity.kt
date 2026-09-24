package com.example.lscontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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

@SuppressLint("MissingPermission")
class MainActivity : Activity() {

    private lateinit var tx: BroadcastController
    private lateinit var badge1: TextView   // 伸縮カード内のバッジ
    private lateinit var badge2: TextView   // 振動カード内のバッジ

    private val ch1Btns = mutableListOf<Button>()
    private val ch2Btns = mutableListOf<Button>()
    private var selCh1 = 0   // 0 = 未選択
    private var selCh2 = 0

    private var pending: (() -> Unit)? = null

    // 青（伸縮）
    private val BL_TEXT   = Color.rgb(0x0C, 0x44, 0x7C)
    private val BL_SEL_BG = Color.rgb(0xE6, 0xF1, 0xFB)
    private val BL_BORDER  = Color.rgb(0x37, 0x8A, 0xDD)

    // ピンク（振動）
    private val PK_TEXT   = Color.rgb(0x72, 0x24, 0x3E)
    private val PK_SEL_BG = Color.rgb(0xFB, 0xEA, 0xF0)
    private val PK_BORDER  = Color.rgb(0xD4, 0x53, 0x7E)

    // ニュートラル
    private val N_BG   = Color.parseColor("#F1EFE8")
    private val N_BTN  = Color.parseColor("#D3D1C7")
    private val N_TEXT = Color.parseColor("#2C2C2A")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        tx = BroadcastController(this) { }
        setContentView(buildUi())
    }

    override fun onStop() {
        super.onStop()
        tx.stopAll()
        clearSel1(); clearSel2()
    }

    // ── UI ──────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(20), dp(14), dp(28))
        }

        // 伸縮カード（青）
        badge1 = TextView(this)
        root.addView(channelCard(
            label      = "伸縮",
            accentText = BL_TEXT,
            accentBg   = BL_SEL_BG,
            accentBorder = BL_BORDER,
            badge      = badge1,
            btns       = ch1Btns,
            onPat      = { n, b -> withPermission { onPat1(n, b) } },
            onStop     = { withPermission { onStop1() } }
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

        root.addView(gap(dp(18)))

        // 全停止
        root.addView(Button(this).apply {
            text = "■ 全停止"; textSize = 17f; isAllCaps = false
            setTextColor(Color.rgb(0x79, 0x1F, 0x1F))
            background = rr(Color.rgb(0xFC, 0xEB, 0xEB), dp(10),
                Color.rgb(0xF0, 0x95, 0x95), dp(1))
            minHeight = dp(64)
            setOnClickListener { withPermission {
                clearSel1(); clearSel2(); tx.stopAll()
            }}
        }, lp(MATCH_PARENT, WRAP_CONTENT))

        return ScrollView(this).apply { addView(root) }
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
            background = rr(Color.WHITE, dp(12), N_BTN, dp(1))
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
                    background = rr(N_BG, dp(8), N_BTN, dp(1))
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
        if (selCh1 == n) { onStop1(); return }
        ch1Btns.forEach { resetBtn(it, N_BG, N_TEXT) }
        selCh1 = n; selBtn(b, BL_SEL_BG, BL_TEXT, BL_BORDER)
        badge1.text = "パターン $n"
        tx.sendCh1(BroadcastController.CH1[n])
    }

    private fun onPat2(n: Int, b: Button) {
        if (selCh2 == n) { onStop2(); return }
        ch2Btns.forEach { resetBtn(it, N_BG, N_TEXT) }
        selCh2 = n; selBtn(b, PK_SEL_BG, PK_TEXT, PK_BORDER)
        badge2.text = "パターン $n"
        tx.sendCh2(BroadcastController.CH2[n])
    }

    private fun onStop1() {
        clearSel1(); tx.stopCh1()
    }

    private fun onStop2() {
        clearSel2(); tx.stopCh2()
    }

    private fun clearSel1() {
        ch1Btns.forEach { resetBtn(it, N_BG, N_TEXT) }
        selCh1 = 0; badge1.text = "停止中"
    }

    private fun clearSel2() {
        ch2Btns.forEach { resetBtn(it, N_BG, N_TEXT) }
        selCh2 = 0; badge2.text = "停止中"
    }

    // ── 描画ヘルパー ──────────────────────────────────────────

    private fun selBtn(b: Button, bg: Int, text: Int, border: Int) {
        b.background = rr(bg, dp(8), border, dp(2))
        b.setTextColor(text); b.setTypeface(b.typeface, Typeface.BOLD)
    }

    private fun resetBtn(b: Button, bg: Int, text: Int) {
        b.background = rr(bg, dp(8), N_BTN, dp(1))
        b.setTextColor(text); b.setTypeface(b.typeface, Typeface.NORMAL)
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
}
