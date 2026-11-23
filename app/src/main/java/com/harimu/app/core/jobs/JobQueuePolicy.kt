package com.harimu.app.core.jobs

enum class EnqueueOutcome {
    START_NOW,
    QUEUED
}

data class QueueSnapshot(
    val activeIds: Set<String>,
    val queuedIds: List<String>
)

/**
 * Pure concurrency-limited FIFO queue policy for heavy background jobs.
 * Allows up to [maxParallel] concurrent jobs (default = 2 per 01-ARSITEKTUR.md).
 */
class JobQueuePolicy(val maxParallel: Int = 2) {

    init {
        require(maxParallel >= 1) { "maxParallel must be >= 1" }
    }

    private val lock = Any()
    private val active = LinkedHashSet<String>()
    private val waiting = ArrayDeque<String>()

    fun enqueue(jobId: String): EnqueueOutcome = synchronized(lock) {
        if (jobId in active || jobId in waiting) {
            return@synchronized if (jobId in active) EnqueueOutcome.START_NOW else EnqueueOutcome.QUEUED
        }
        if (active.size < maxParallel) {
            active.add(jobId)
            EnqueueOutcome.START_NOW
        } else {
            waiting.addLast(jobId)
            EnqueueOutcome.QUEUED
        }
    }

    fun completeAndDequeueNext(jobId: String): String? = synchronized(lock) {
        active.remove(jobId)
        waiting.remove(jobId)
        promoteIfSlotAvailable()
    }

    fun cancel(jobId: String): Pair<Boolean, String?> = synchronized(lock) {
        val removed = active.remove(jobId) || waiting.remove(jobId)
        val promoted = promoteIfSlotAvailable()
        removed to promoted
    }

    fun restore(runningIds: List<String>, queuedIds: List<String>): List<String> = synchronized(lock) {
        active.clear()
        waiting.clear()
        val combined = (runningIds + queuedIds).distinct()
        val toRunNow = mutableListOf<String>()
        for (id in combined) {
            if (active.size < maxParallel) {
                active.add(id)
                toRunNow.add(id)
            } else {
                waiting.addLast(id)
            }
        }
        toRunNow
    }

    fun snapshot(): QueueSnapshot = synchronized(lock) {
        QueueSnapshot(activeIds = active.toSet(), queuedIds = waiting.toList())
    }

    fun clear() = synchronized(lock) {
        active.clear()
        waiting.clear()
    }

    private fun promoteIfSlotAvailable(): String? {
        if (active.size < maxParallel && waiting.isNotEmpty()) {
            val next = waiting.removeFirst()
            active.add(next)
            return next
        }
        return null
    }
}
