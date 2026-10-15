package com.sengine

import com.sengine.engine.jobs.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class JobSafetyTest {
    @Test fun parallelJobsVisitEachIndexExactlyOnce() {
        val jobs = JobSystem()
        try {
            val hits = java.util.concurrent.atomic.AtomicIntegerArray(101)
            jobs.scheduleParallel(object : IJobParallelFor { override fun execute(index: Int) { hits.incrementAndGet(index) } }, 101, 7).complete()
            repeat(101) { assertEquals(1, hits.get(it)) }
        } finally { jobs.shutdown() }
    }
    @Test fun workerFailuresReachTheCaller() {
        val jobs = JobSystem()
        try {
            val handle = jobs.schedule(object : IJob { override fun execute() { error("broken job") } })
            try { handle.complete(); fail("Job failure was swallowed") }
            catch (e: IllegalStateException) { assertEquals("broken job", e.cause?.message) }
        } finally { jobs.shutdown() }
    }
    @Test fun invalidBatchSizeFailsInsteadOfLoopingForever() {
        val jobs = JobSystem()
        try {
            try { jobs.scheduleParallel(object : IJobParallelFor { override fun execute(index: Int) {} }, 100, 0); fail() }
            catch (_: IllegalArgumentException) {}
            try { jobs.scheduleBatch(object : IJobParallelForBatch { override fun execute(startIndex: Int, count: Int) {} }, 100, -1); fail() }
            catch (_: IllegalArgumentException) {}
        } finally { jobs.shutdown() }
    }
    @Test fun shuttingDownOneSchedulerDoesNotAffectAnother() {
        val first = JobSystem(); val second = JobSystem()
        try {
            first.shutdown()
            val counter = AtomicInteger()
            second.schedule(object : IJob { override fun execute() { counter.incrementAndGet() } }).complete()
            assertEquals(1, counter.get())
        } finally { first.shutdown(); second.shutdown() }
    }
}
