package org.cyberb.claudevoice

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.LineBackgroundSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainView(private val host: VoiceHost, root: View) {

    private val activity = host.activity
    private val ui = host.scope
    private val http get() = host.http
    private fun prefs() = host.prefs()

    private val transcript: TextView = root.findViewById(R.id.transcript)
    private val scroll: ScrollView = root.findViewById(R.id.scroll)
    private val bottombar: View = root.findViewById(R.id.bottombar)
    private val status: TextView = root.findViewById(R.id.status)
    private val workdir: TextView = root.findViewById(R.id.workdir)
    private val branch: TextView = root.findViewById(R.id.branch)
    private val talk: FloatingActionButton = root.findViewById(R.id.talk)

    private val audio = AudioEngine(
        host,
        onReady = { setBusy(false); setStatus("ready") },
        onDevicesChanged = { updateBottom() },
    )

    private var chatJob: Job? = null
    private var busy = false
    private var tokIn = 0
    private var tokOut = 0
    private var thinkingStart = 0L
    private var statusWord = "ready"
    private var loadingHistory = false

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { updateStatusLine(); ticker.postDelayed(this, 1000) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindTalk() {
        talk.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { if (busy) interrupt() else startRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (audio.isRecording()) stopAndSend(); true }
                else -> false
            }
        }
    }

    init {
        bindTalk()
        root.findViewById<TextView>(R.id.overflowBtn).setOnClickListener { v ->
            val pm = PopupMenu(activity, v)
            pm.menu.add(0, 1, 0, R.string.clear_short)
            pm.menu.add(0, 2, 1, R.string.compact_short)
            pm.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { clearAgent(); true }
                    2 -> { compactAgent(); true }
                    else -> false
                }
            }
            pm.show()
        }
    }

    fun onServiceEvent(type: String, text: String) {
        when (type) {
            "you" -> appendYou(text)
            "reply" -> appendReply(text)
            "status" -> {
                setStatus(text)
                val busyNow = text == "listening…" || text == "thinking…" || text == "speaking…"
                if (busyNow != busy) setBusy(busyNow)
            }
        }
    }

    fun onResume() {
        audio.applyVoice()
        applyBarPosition()
    }

    fun destroy() {
        audio.destroy()
    }

    private fun applyBarPosition() {
        val top = prefs().getBoolean("statusbarTop", false)
        val barLp = bottombar.layoutParams as RelativeLayout.LayoutParams
        val scrollLp = scroll.layoutParams as RelativeLayout.LayoutParams
        if (top) {
            barLp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            barLp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            scrollLp.removeRule(RelativeLayout.ABOVE)
            scrollLp.addRule(RelativeLayout.BELOW, R.id.bottombar)
        } else {
            barLp.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
            barLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            scrollLp.removeRule(RelativeLayout.BELOW)
            scrollLp.addRule(RelativeLayout.ABOVE, R.id.bottombar)
        }
        bottombar.layoutParams = barLp
        scroll.layoutParams = scrollLp
    }

    private fun clearAgent() {
        val id = host.currentAgentId ?: return
        ui.launch {
            http.clear(id)
            host.transcripts.remove(id)
            host.ctxByAgent.remove(id)
            tokIn = 0; tokOut = 0
            showTranscript()
            updateBottom()
            setStatus("cleared")
            host.drawerView.close()
        }
    }

    private fun compactAgent() {
        val id = host.currentAgentId ?: return
        host.drawerView.close()
        tokIn = 0; tokOut = 0
        setBusy(true)
        startTimer("compacting…")
        chatJob = ui.launch {
            val ok = http.compact(id)
            stopThinking()
            setBusy(false)
            if (!ok) { setStatus("compact failed"); return@launch }
            appendSpan(colored("— conversation compacted —\n\n", R.color.action_text, italic = true))
            host.ctxByAgent.remove(id)
            tokIn = 0; tokOut = 0
            updateBottom()
            setStatus("compacted")
        }
    }

    private fun micColor(res: Int) {
        talk.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, res))
    }

    fun setBusy(b: Boolean) {
        busy = b
        if (b) {
            talk.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            micColor(R.color.mic_busy)
        } else {
            talk.setImageResource(android.R.drawable.ic_btn_speak_now)
            micColor(R.color.mic_idle)
        }
    }

    private fun interrupt() {
        audio.stopSpeaking()
        stopThinking()
        http.cancel()
        chatJob?.cancel()
        audio.abortCapture()
        setBusy(false)
        setStatus("stopped")
        if (prefs().getBoolean("running", false)) {
            try { activity.startService(Intent(activity, VoiceService::class.java).setAction(VoiceService.ACTION_CANCEL)) } catch (e: Exception) { }
        }
    }

    fun updateBottom() {
        savePrefs()
        val a = host.agents.firstOrNull { it.id == host.currentAgentId }
        if (a == null) {
            workdir.text = activity.getString(R.string.no_agent)
            branch.visibility = View.GONE
            return
        }
        workdir.text = shortPath(a.dir)
        val b = a.branch?.let { it + if (a.dirty) " ✗" else "" } ?: ""
        branch.visibility = View.VISIBLE
        branch.text = (b + "   🎙 " + audio.micLabel()).trim()
        branch.setTextColor(ContextCompat.getColor(activity,
            if (a.dirty) R.color.branch_dirty else R.color.branch_text))
    }

    private fun startRecording() {
        if (audio.isRecording()) return
        if (!audio.hasMic()) { setStatus("grant microphone permission"); return }
        if (!audio.startCapture()) { setStatus("microphone unavailable"); return }
        micColor(R.color.mic_recording)
        setStatus("listening…")
    }

    private fun stopAndSend() {
        val wavBytes = audio.stopCapture() ?: run { setStatus("nothing recorded"); setBusy(false); return }
        val aid = host.currentAgentId
        if (aid == null) { setStatus("no agent selected — swipe right to add one"); setBusy(false); return }
        setBusy(true)
        setStatus("transcribing…")
        audio.speakCue("transcribing")
        chatJob = ui.launch {
            val said = http.stt(wavBytes)
            if (said.isNullOrBlank()) { setStatus("speech-to-text failed"); setBusy(false); return@launch }
            appendYou(said)
            startThinking()
            audio.speakCue("thinking")
            streamChat(aid, said)
        }
    }

    private suspend fun streamChat(aid: Int, text: String) {
        val narrate = prefs().getBoolean("narrate_$aid", false)
        var sawReply = false
        val ok = http.chat(text, aid, narrate) { event ->
            if (event is ChatEvent.Reply) sawReply = true
            withContext(Dispatchers.Main) { handleEvent(event) }
        }
        if ((!ok || !sawReply) && busy) { stopThinking(); setStatus("agent failed"); setBusy(false) }
    }

    private fun handleEvent(e: ChatEvent) {
        when (e) {
            is ChatEvent.Action -> appendAction(e.label)
            is ChatEvent.Diff -> appendDiff(e.file, e.patch)
            is ChatEvent.Working -> {
                appendAction(e.text)
                audio.speakWorking(e.text)
            }
            is ChatEvent.Usage -> {
                e.tokIn?.let { tokIn = it }
                e.tokOut?.let { tokOut = it }
                val id = host.currentAgentId ?: -1
                val max = e.max ?: (host.ctxByAgent[id]?.second ?: 0)
                host.ctxByAgent[id] = Pair(tokIn, max)
                updateStatusLine()
                updateBottom()
            }
            is ChatEvent.Reply -> {
                stopThinking()
                appendReply(e.text)
                setStatus("speaking…")
                audio.speakReply(e.text, e.speech)
            }
            else -> {}
        }
    }

    private fun buffer(): SpannableStringBuilder =
        host.transcripts.getOrPut(host.currentAgentId ?: -1) { SpannableStringBuilder() }

    private fun appendSpan(cs: CharSequence) {
        buffer().append(cs)
        if (loadingHistory) return
        transcript.setText(buffer(), TextView.BufferType.SPANNABLE)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun showTranscript() {
        val buf = host.transcripts.getOrPut(host.currentAgentId ?: -1) { SpannableStringBuilder() }
        transcript.setText(buf, TextView.BufferType.SPANNABLE)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun renderHistory(events: List<ChatEvent>) {
        loadingHistory = true
        for (e in events) renderEvent(e)
        loadingHistory = false
    }

    private fun renderEvent(e: ChatEvent) {
        when (e) {
            is ChatEvent.You -> appendYou(e.text)
            is ChatEvent.Action -> appendAction(e.label)
            is ChatEvent.Diff -> appendDiff(e.file, e.patch)
            is ChatEvent.Reply -> appendReply(e.text)
            else -> {}
        }
    }

    private fun colored(text: String, colorRes: Int, mono: Boolean = false, italic: Boolean = false): SpannableString {
        val s = SpannableString(text)
        s.setSpan(ForegroundColorSpan(ContextCompat.getColor(activity, colorRes)), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (mono) s.setSpan(TypefaceSpan("monospace"), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (italic) s.setSpan(StyleSpan(Typeface.ITALIC), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    private fun appendYou(text: String) {
        appendSpan(colored("you  ", R.color.branch_text))
        appendSpan("$text\n\n")
    }

    private fun stripInline(t: String): String {
        var s = t
        s = Regex("`([^`]*)`").replace(s, "$1")
        s = Regex("\\[([^\\]]+)\\]\\([^)]*\\)").replace(s, "$1")
        s = Regex("^\\s{0,3}[-*+]\\s+", RegexOption.MULTILINE).replace(s, "• ")
        s = Regex("(\\*\\*|\\*|__|_|#+|>|~~|~)").replace(s, "")
        return s
    }

    private fun appendReply(text: String) {
        val parts = text.split("```")
        for ((i, part) in parts.withIndex()) {
            if (i % 2 == 0) {
                val t = stripInline(part).trim()
                if (t.isNotEmpty()) appendSpan("$t\n")
            } else {
                var code = part
                val nl = code.indexOf('\n')
                if (nl >= 0) {
                    val first = code.substring(0, nl).trim()
                    if (first.isNotEmpty() && !first.contains(' ') && first.length < 15) {
                        code = code.substring(nl + 1)
                    }
                }
                appendSpan(codeBlock(code))
            }
        }
        appendSpan("\n")
    }

    private fun col(res: Int) = ContextCompat.getColor(activity, res)

    private val codeKeywords = setOf(
        "val", "var", "fun", "def", "class", "interface", "object", "return", "if", "else", "for",
        "while", "do", "when", "switch", "case", "break", "continue", "import", "package", "public",
        "private", "protected", "static", "final", "void", "new", "this", "super", "try", "catch",
        "finally", "throw", "throws", "func", "let", "const", "type", "struct", "enum", "defer",
        "range", "map", "chan", "select", "async", "await", "yield", "lambda", "in", "is", "as",
        "and", "or", "not", "true", "false", "null", "nil", "none", "int", "string", "bool",
        "boolean", "float", "double", "long", "char", "byte", "echo", "print", "println", "with",
        "from", "global", "pass", "raise", "except", "elif", "using", "namespace", "template",
        "unsigned", "virtual", "override", "suspend", "data", "sealed", "companion", "init", "by"
    )

    private fun codeBlock(code: String): CharSequence {
        val body = code.trimEnd('\n') + "\n"
        val sb = SpannableStringBuilder(body)
        val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        sb.setSpan(ForegroundColorSpan(col(R.color.code_text)), 0, sb.length, flag)
        sb.setSpan(TypefaceSpan("monospace"), 0, sb.length, flag)
        sb.setSpan(CodeBlockBg(col(R.color.code_bg)), 0, sb.length, flag)
        highlightInto(sb, body)
        return sb
    }

    private fun highlightInto(sb: SpannableStringBuilder, code: String) {
        val n = code.length
        var i = 0
        fun span(s: Int, e: Int, c: Int) =
            sb.setSpan(ForegroundColorSpan(c), s, e, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val kw = col(R.color.code_kw); val str = col(R.color.code_str)
        val com = col(R.color.code_com); val num = col(R.color.code_num)
        while (i < n) {
            val c = code[i]
            when {
                c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                    val s = i; while (i < n && code[i] != '\n') i++; span(s, i, com)
                }
                c == '#' -> { val s = i; while (i < n && code[i] != '\n') i++; span(s, i, com) }
                c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                    val s = i; i += 2
                    while (i + 1 < n && !(code[i] == '*' && code[i + 1] == '/')) i++
                    i = minOf(n, i + 2); span(s, i, com)
                }
                c == '"' || c == '\'' || c == '`' -> {
                    val q = c; val s = i; i++
                    while (i < n && code[i] != q) { if (code[i] == '\\') i++; i++ }
                    i = minOf(n, i + 1); span(s, i, str)
                }
                c.isDigit() -> {
                    val s = i
                    while (i < n && (code[i].isLetterOrDigit() || code[i] == '.')) i++
                    span(s, i, num)
                }
                c.isLetter() || c == '_' -> {
                    val s = i
                    while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                    if (code.substring(s, i) in codeKeywords) span(s, i, kw)
                }
                else -> i++
            }
        }
    }

    private class CodeBlockBg(private val bg: Int) : LineBackgroundSpan {
        override fun drawBackground(
            canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int,
            baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, lineNumber: Int
        ) {
            val orig = paint.color
            paint.color = bg
            canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
            paint.color = orig
        }
    }

    private fun appendAction(label: String) {
        appendSpan(colored("▸ $label\n", R.color.action_text, italic = true))
    }

    private fun appendDiff(file: String, patch: String) {
        appendSpan(colored("✎ $file\n", R.color.action_text, italic = true))
        for (line in patch.split("\n")) {
            val color = when {
                line.startsWith("+") -> R.color.diff_add
                line.startsWith("-") -> R.color.diff_del
                else -> R.color.action_text
            }
            appendSpan(colored("$line\n", color, mono = true))
        }
        appendSpan("\n")
    }

    fun setStatus(s: String) { statusWord = s; updateStatusLine() }

    private fun updateStatusLine() {
        val sb = StringBuilder(statusWord)
        if (statusWord == "thinking…" || statusWord == "compacting…") {
            sb.append("  ").append((System.currentTimeMillis() - thinkingStart) / 1000).append("s")
        }
        val ctx = host.ctxByAgent[host.currentAgentId ?: -1]
        if (ctx != null && ctx.first > 0) {
            sb.append("   ctx ").append(fmtTok(ctx.first))
            if (ctx.second > 0) sb.append("/").append(fmtTok(ctx.second))
        }
        if (tokIn > 0 || tokOut > 0) {
            sb.append("   ↑").append(fmtTok(tokIn)).append(" ↓").append(fmtTok(tokOut))
        }
        status.text = sb.toString()
    }

    private fun savePrefs() {
        prefs().edit().putInt("agent", host.currentAgentId ?: -1).apply()
    }

    private fun fmtTok(n: Int) = if (n >= 1000) "${n / 1000}k" else "$n"

    private fun startTimer(label: String) {
        thinkingStart = System.currentTimeMillis()
        setStatus(label)
        ticker.removeCallbacks(tick); ticker.post(tick)
    }

    private fun startThinking() {
        tokIn = 0; tokOut = 0
        startTimer("thinking…")
    }

    private fun stopThinking() { ticker.removeCallbacks(tick) }
}
