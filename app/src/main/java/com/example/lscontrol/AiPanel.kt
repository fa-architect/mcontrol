package com.example.lscontrol

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ローカルAI（llama.cpp）の入力欄とモデル選択。
 * 手順3：AIの答えを表示するだけ。機器への送信はまだしない。
 * 送信のたびに（2回目以降）モデルを読み込み直して、会話の履歴を消す。
 */
class AiPanel(
    private val act: Activity,
    private val dp: (Int) -> Int,
    private val onCommands: (List<AiCommand>) -> Unit = {},
    private val onStopAll: () -> Unit = {}
) {

    companion object {
        const val REQ_PICK_MODEL = 2001
        private const val MODELS_DIR = "models"
        private const val PREDICT_LEN = 128
        /** Qwen用のプロンプト。空なら送らない */
        private val SYSTEM_PROMPT = """
            あなたは機器の命令を作る変換器です。
            ユーザーの言葉を、次の形式の行に変換してください。

            形式: チャンネル パターン 秒数
            - チャンネル: all（全体）/ stroke（伸縮）/ vibe（振動）
            - パターン: 0〜3 の整数（0=停止、1=弱い、2=普通、3=強い）
            - 秒数: 1〜60 の整数

            ユーザーの言葉の「弱く」「強く」を、それぞれのチャンネルに正しく当てはめてください。
            1行に1つの命令を書き、それ以外は何も書かないでください。

            例1
            入力: 弱く動かして
            出力:
            all 1 30

            例2
            入力: 振動だけ普通で
            出力:
            vibe 2 30

            例3
            入力: 止めて
            出力:
            all 0 1

            例4
            入力: だんだん強くして
            出力:
            all 1 10
            all 2 10
            all 3 10
        """.trimIndent()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: InferenceEngine? = null
    private var genJob: Job? = null
    private var ready = false
    private var modelPath: String? = null
    /** 一度でも送信したら true。次の送信の前に読み込み直して履歴を消す */
    private var used = false

    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var sendBtn: Button
    private lateinit var output: TextView

    // ── UI ──────────────────────────────────────────────────────

    fun build(): View {
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#3A3A3E"))
            }
        }

        val header = android.widget.LinearLayout(act).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(act).apply {
            text = "AI（端末内）"; textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(android.widget.Button(act).apply {
            text = "■ 全停止"; textSize = 12f; isAllCaps = false
            setTextColor(Color.rgb(0xF0, 0x95, 0x95))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(0x3A, 0x1A, 0x1A))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.rgb(0xF0, 0x95, 0x95))
            }
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { onStopAll() }
        }, android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        card.addView(header)

        status = TextView(act).apply { text = "モデル未選択"; textSize = 12f; setTextColor(Color.rgb(0x8E, 0x8E, 0x93)) }
        card.addView(status)

        card.addView(android.widget.Button(act).apply {
            text = "モデル選択"; isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#2C2C2E"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#3A3A3E"))
            }
            setOnClickListener { this@AiPanel.pickModel() }
        }, lp().also { it.topMargin = dp(6) })

        input = EditText(act).apply {
            hint = "例：振動だけ強く / 伸縮だけゆっくり / だんだん強くして\nそのあと振動だけ弱く / 両方止めて"
            isSingleLine = false
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(0x63, 0x63, 0x6F))
        }
        card.addView(input, lp())

        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        sendBtn = Button(act).apply {
            text = "送信"; isAllCaps = false; isEnabled = false
            setOnClickListener { send() }
        }
        row.addView(sendBtn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.marginEnd = dp(6) })
        row.addView(Button(act).apply {
            text = "AI中断"; isAllCaps = false
            setOnClickListener { this@AiPanel.cancel() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        card.addView(row, lp())

        output = TextView(act).apply {
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
        }
        card.addView(output, lp().also { it.topMargin = dp(6) })

        // エンジンを先に用意する（サンプルと同じく別スレッドで取得）
        scope.launch {
            try {
                val eng = withContext(Dispatchers.Default) {
                    AiChat.getInferenceEngine(act.applicationContext)
                }
                engine = eng
                // 画面の作り直し後はモデルの場所がわからないため、選び直してもらう
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = "AIの初期化に失敗: ${e.message}"
            }
        }
        return card
    }

    // ── モデル選択 ──────────────────────────────────────────────

    private fun pickModel() {
        this@AiPanel.cancel()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        act.startActivityForResult(i, REQ_PICK_MODEL)
    }

    /** MainActivity の onActivityResult から呼ぶ */
    fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req != REQ_PICK_MODEL || res != Activity.RESULT_OK) return
        data?.data?.let { loadFrom(it) }
    }

    private fun loadFrom(uri: Uri) {
        ready = false
        sendBtn.isEnabled = false
        scope.launch {
            try {
                val name = displayName(uri) ?: "model.gguf"
                status.text = "コピー中… $name"
                val file = withContext(Dispatchers.IO) { copyIfNeeded(uri, name) }

                status.text = "読み込み中… $name"
                val eng = engine ?: withContext(Dispatchers.Default) {
                    AiChat.getInferenceEngine(act.applicationContext)
                }.also { engine = it }

                waitSettled(eng)
                freshLoad(eng, file.path)
                modelPath = file.path
                used = false
                setReady("準備完了: $name")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = "読み込み失敗: ${e.message}"
            }
        }
    }

    /** アプリ専用の保存場所にコピー（同じ名前が既にあればコピーしない） */
    private fun copyIfNeeded(uri: Uri, name: String): File {
        val dir = File(act.filesDir, MODELS_DIR).apply { mkdirs() }
        val f = File(dir, name)
        if (!f.exists() || f.length() == 0L) {
            val tmp = File(dir, "$name.part")
            val inp = act.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("ファイルを開けません")
            inp.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            if (!tmp.renameTo(f)) throw IllegalStateException("コピー後の名前変更に失敗")
        }
        return f
    }

    private fun displayName(uri: Uri): String? =
        act.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    // ── 送信 ────────────────────────────────────────────────────

    private fun send() {
        val eng = engine ?: return
        val path = modelPath ?: return
        val text = input.text.toString().trim()
        if (!ready || text.isEmpty()) return

        genJob?.cancel()
        output.text = ""
        sendBtn.isEnabled = false
        val sb = StringBuilder()

        genJob = scope.launch {
            try {
                val t0 = SystemClock.elapsedRealtime()
                val needReset = used
                used = true   // 途中で中断されても、次回は必ず読み込み直す
                if (needReset) {
                    status.text = "履歴を消去中…"
                    waitSettled(eng)
                    freshLoad(eng, path)
                }
                val t1 = SystemClock.elapsedRealtime()

                status.text = "生成中…"
                var tFirst = -1L
                withContext(Dispatchers.Default) {
                    eng.sendUserPrompt("$text /no_think", PREDICT_LEN).collect { tok ->
                        withContext(Dispatchers.Main) {
                            if (tFirst < 0) tFirst = SystemClock.elapsedRealtime()
                            sb.append(tok)
                            output.text = sb.toString()
                        }
                    }
                }
                val t2 = SystemClock.elapsedRealtime()
                val first = if (tFirst < 0) "-" else "${tFirst - t1}ms"
                val cmds = parseCommands(sb.toString())
                status.text = "完了（消去 ${t1 - t0}ms / 最初の文字 $first / 合計 ${t2 - t0}ms）" +
                    if (cmds.isNotEmpty()) "\n${cmds.size}件の命令" else ""
                if (cmds.isNotEmpty()) onCommands(cmds)
            } catch (e: CancellationException) {
                status.text = "中断しました"
            } catch (e: Exception) {
                status.text = "生成失敗: ${e.message}"
            } finally {
                sendBtn.isEnabled = ready
            }
        }
    }

    private fun parseCommands(text: String): List<AiCommand> {
        val valid = setOf("all", "stroke", "vibe")
        return text.lines().mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size != 3) return@mapNotNull null
            val ch = p[0]; if (ch !in valid) return@mapNotNull null
            val pat = p[1].toIntOrNull() ?: return@mapNotNull null
            if (pat !in 0..3) return@mapNotNull null
            val sec = p[2].toIntOrNull() ?: return@mapNotNull null
            if (sec !in 1..60) return@mapNotNull null
            AiCommand(ch, pat, sec)
        }
    }

        /** 待機中・読み込み前・エラーのどれかになるまで待つ（生成中などは待つ） */
    private suspend fun waitSettled(eng: InferenceEngine) {
        eng.state.first {
            it is InferenceEngine.State.ModelReady ||
                it is InferenceEngine.State.Initialized ||
                it is InferenceEngine.State.Error
        }
    }

    /** モデルを外して読み込み直し、プロンプトを送る（会話の履歴が消える） */
    private suspend fun freshLoad(eng: InferenceEngine, path: String) =
        withContext(Dispatchers.IO) {
            when (eng.state.value) {
                is InferenceEngine.State.ModelReady,
                is InferenceEngine.State.Error -> eng.cleanUp()
                else -> {}
            }
            eng.loadModel(path)
            if (SYSTEM_PROMPT.isNotEmpty()) eng.setSystemPrompt(SYSTEM_PROMPT)
        }

    // ── 停止・後片付け ──────────────────────────────────────────

    /** 生成を止める（全停止・画面から離れた時にも呼ぶ） */
    fun cancel() {
        genJob?.cancel()
        genJob = null
    }

    /** MainActivity の onDestroy から呼ぶ */
    fun destroy() {
        this@AiPanel.cancel()
        scope.cancel()
        if (act.isFinishing) engine?.destroy()
    }

    private fun setReady(msg: String) {
        ready = true
        sendBtn.isEnabled = true
        status.text = msg
    }

    private fun lp() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
}
