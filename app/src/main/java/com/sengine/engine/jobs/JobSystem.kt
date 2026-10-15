package com.sengine.engine.jobs

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM executor-backed job scheduler. This is not a native/Burst compiler.
 */

class JobHandle internal constructor(internal val future: Future<*>? = null, internal val id: Int = 0) {
    fun complete() {
        try { future?.get() }
        catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IllegalStateException("Job wait interrupted", e) }
        catch (e: ExecutionException) { throw IllegalStateException("Job execution failed", e.cause) }
    }
    fun isCompleted(): Boolean = future?.isDone ?: true

    companion object {
        fun combine(vararg handles: JobHandle): JobHandle {
            // Wait for all
            for (h in handles) h.complete()
            return JobHandle(null)
        }
    }
}

interface IJob {
    fun execute()
}

interface IJobParallelFor {
    fun execute(index: Int)
}

interface IJobParallelForBatch {
    fun execute(startIndex: Int, count: Int)
}

class JobSystem {
    private val executor: ExecutorService = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 8),
        ThreadFactory { r ->
            Thread(r, "S-Job-${jobCounter.incrementAndGet()}").apply { isDaemon = true }
        }
    )
    private val jobIdCounter = AtomicInteger(0)

    companion object {
        private val jobCounter = AtomicInteger(0)
        val main = JobSystem()
    }

    fun schedule(job: IJob): JobHandle {
        val future = executor.submit { job.execute() }
        return JobHandle(future, jobIdCounter.incrementAndGet())
    }

    fun scheduleParallel(job: IJobParallelFor, count: Int, batchSize: Int = 32): JobHandle {
        require(count >= 0 && batchSize > 0) { "Count must be non-negative and batch size positive" }
        val futures = mutableListOf<Future<*>>()
        var start = 0
        while (start < count) {
            val end = minOf(start + batchSize, count)
            val s = start
            val e = end
            futures.add(executor.submit {
                for (i in s until e) job.execute(i)
            })
            start = end
        }
        val combined = executor.submit {
            for (f in futures) f.get()
        }
        return JobHandle(combined, jobIdCounter.incrementAndGet())
    }

    fun scheduleBatch(job: IJobParallelForBatch, count: Int, batchSize: Int = 32): JobHandle {
        require(count >= 0 && batchSize > 0) { "Count must be non-negative and batch size positive" }
        val futures = mutableListOf<Future<*>>()
        var start = 0
        while (start < count) {
            val c = minOf(batchSize, count - start)
            val s = start
            futures.add(executor.submit { job.execute(s, c) })
            start += c
        }
        val combined = executor.submit {
            for (f in futures) f.get()
        }
        return JobHandle(combined, jobIdCounter.incrementAndGet())
    }

    fun completeAll(vararg handles: JobHandle) {
        for (h in handles) h.complete()
    }

    fun shutdown() {
        executor.shutdown()
    }
}

/**
 * NativeArray equivalent - contiguous memory for jobs (simplified)
 */
class NativeArray<T>(size: Int, initial: T? = null) {
    private val data = arrayOfNulls<Any>(size) as Array<T?>
    val length: Int = size

    init {
        if (initial != null) for (i in 0 until size) data[i] = initial
    }

    operator fun get(index: Int): T = data[index]!!
    operator fun set(index: Int, value: T) { data[index] = value }

    fun toList(): List<T> = data.filterNotNull().map { it as T }
}

/**
 * Burst-like math jobs
 */
object MathJobs {
    fun transformPositions(positions: NativeArray<FloatArray>, matrices: NativeArray<FloatArray>): JobHandle {
        return JobSystem.main.scheduleParallel(object : IJobParallelFor {
            override fun execute(index: Int) {
                val pos = positions[index]
                val mat = matrices[index]
                val x = pos[0]; val y = pos[1]; val z = pos[2]
                pos[0] = mat[0] * x + mat[4] * y + mat[8] * z + mat[12]
                pos[1] = mat[1] * x + mat[5] * y + mat[9] * z + mat[13]
                pos[2] = mat[2] * x + mat[6] * y + mat[10] * z + mat[14]
            }
        }, positions.length, 64)
    }
}
