package com.maxrave.simpmusic.shiroikuma.automation

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one place an export is started, stopped and found.
 *
 * Two invariants the 保存復元 contract turns on:
 *
 * - **The running guard is process-local and released in a `finally`.** Persisting it would wedge
 *   the app for good after a single crash, and any later request would answer
 *   `ERROR:export already running` with no way back short of killing the process.
 * - **Cancel sets a `@Volatile` flag the write loop checks between entries**, so an export unwinds
 *   at a safe boundary instead of being torn down mid-`write()`. The partial file is deleted by
 *   whoever was writing it, on the same `finally` that handles any other failure.
 *
 * Both the Export/Import panel and the automation service run through here, so there is exactly one
 * way to unwind and it always deletes the partial.
 */
object ExportControl {
    private val running = AtomicBoolean(false)

    @Volatile
    private var cancelled = false

    /** The `reply_id` of the run in progress, or null. A cancel may name it, or name nothing. */
    @Volatile
    var currentReplyId: String? = null
        private set

    /** False when an export is already in flight — §1 forbids two at once. */
    fun begin(replyId: String?): Boolean {
        if (!running.compareAndSet(false, true)) return false
        cancelled = false
        currentReplyId = replyId
        return true
    }

    fun end() {
        currentReplyId = null
        cancelled = false
        running.set(false)
    }

    fun isRunning(): Boolean = running.get()

    /**
     * Ask the running export to stop. A cancel that arrives when nothing is running, or after the
     * export already finished, is a **silent no-op** — 自由作業盤 fires it whenever 白い熊 presses
     * 中止, without knowing how far we got.
     */
    fun requestCancel(replyId: String? = null) {
        if (!running.get()) return
        if (replyId != null && currentReplyId != null && replyId != currentReplyId) return
        cancelled = true
    }

    fun isCancelled(): Boolean = cancelled
}
