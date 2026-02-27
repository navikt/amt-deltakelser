package no.nav.amt.lib.utils.job

import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class JobManagerTest {
    @Test
    fun `startJob - to jobber - skal kjøre samtidig`(): Unit = runBlocking {
        val jobManager = JobManager(isLeader = { true }, applicationIsReady = { true })
        val counter = AtomicInteger(0)

        jobManager.startJob(
            name = "jobb1",
            initialDelay = Duration.ofMillis(0),
            period = Duration.ofMillis(100),
        ) {
            counter.incrementAndGet()
        }

        jobManager.startJob(
            name = "jobb2",
            initialDelay = Duration.ofMillis(0),
            period = Duration.ofMillis(100),
        ) {
            counter.incrementAndGet()
        }

        delay(150)

        jobManager.stopJobs()

        counter.get() shouldBe 4
    }

    @Test
    fun `stopJobs - skal stoppe jobber`() = runTest {
        val jobManager = JobManager(isLeader = { true }, applicationIsReady = { true })
        val counter = AtomicInteger(0)

        jobManager.startJob(
            name = "jobb1",
            initialDelay = Duration.ofMillis(0),
            period = Duration.ofMillis(50),
        ) {
            counter.incrementAndGet()
        }

        Thread.sleep(120)

        jobManager.stopJobs()

        val valueAfterStop = counter.get()

        Thread.sleep(100)

        counter.get() shouldBe valueAfterStop
    }

    @Test
    fun `startJob - jobb med lang kjøretid - skal ikke starte nye jobber før forrige er ferdig`() = runTest {
        val jobManager = JobManager(isLeader = { true }, applicationIsReady = { true })
        val counter = AtomicInteger(0)

        jobManager.startJob(
            name = "jobb1",
            initialDelay = Duration.ofMillis(0),
            period = Duration.ofMillis(50),
        ) {
            delay(100)
            counter.incrementAndGet()
        }

        Thread.sleep(220)

        jobManager.stopJobs()

        counter.get() shouldBe 2
    }

    @Test
    fun `startJob - skal respektere delay og period`() = runTest {
        val jobManager = JobManager(isLeader = { true }, applicationIsReady = { true })
        val executionTimes = Collections.synchronizedList(mutableListOf<Long>())
        val initialDelay = Duration.ofMillis(150)
        val period = Duration.ofMillis(100)
        val testStartTime = System.currentTimeMillis()
        val marginOfError = 50

        jobManager.startJob(
            name = "test-jobb",
            initialDelay = initialDelay,
            period = period,
        ) {
            executionTimes.add(System.currentTimeMillis())
        }

        // Wait long enough for 3 executions
        Thread.sleep(initialDelay.toMillis() + 2 * period.toMillis() + marginOfError)

        jobManager.stopJobs()

        executionTimes.size shouldBe 3

        // Check initial delay
        val firstExecutionTime = executionTimes.first()
        val actualInitialDelay = firstExecutionTime - testStartTime
        actualInitialDelay shouldBeGreaterThanOrEqual initialDelay.toMillis()

        // Check period
        val firstPeriod = executionTimes[1] - executionTimes[0]
        val secondPeriod = executionTimes[2] - executionTimes[1]

        firstPeriod shouldBeGreaterThanOrEqual period.toMillis() - marginOfError
        secondPeriod shouldBeGreaterThanOrEqual period.toMillis() - marginOfError
    }
}
