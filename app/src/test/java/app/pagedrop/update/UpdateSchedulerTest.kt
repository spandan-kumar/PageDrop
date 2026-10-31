package app.pagedrop.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSchedulerTest {
    private class FakeWork : UpdateWork {
        val calls = mutableListOf<String>()
        override fun schedulePeriodic() { calls += "schedule" }
        override fun cancelPeriodic() { calls += "cancel" }
        override fun runNow(installAfter: Boolean) { calls += if (installAfter) "now+install" else "now" }
    }

    @Test fun schedulesWhenTheBuildAndTheSwitchAreOn() {
        val work = FakeWork()
        UpdateScheduler(true, { true }, work).sync()
        assertEquals(listOf("schedule"), work.calls)
    }

    @Test fun cancelsWhenTheSwitchIsOff() {
        val work = FakeWork()
        UpdateScheduler(true, { false }, work).sync()
        assertEquals(listOf("cancel"), work.calls)
    }

    @Test fun followsTheSwitchAsItChanges() {
        val work = FakeWork()
        var on = true
        val scheduler = UpdateScheduler(true, { on }, work)
        scheduler.sync(); on = false; scheduler.sync(); on = true; scheduler.sync()
        assertEquals(listOf("schedule", "cancel", "schedule"), work.calls)
    }

    @Test fun doesNothingInBuildsWithoutSelfUpdate() {
        val work = FakeWork()
        val scheduler = UpdateScheduler(false, { true }, work)
        scheduler.sync(); scheduler.checkNow()
        assertEquals(emptyList<String>(), work.calls)
    }

    @Test fun checkNowCanAskToInstallAfterwards() {
        val work = FakeWork()
        UpdateScheduler(true, { true }, work).checkNow(installAfter = true)
        assertEquals(listOf("now+install"), work.calls)
    }

    @Test fun checkNowRunsOnceInSelfUpdateBuilds() {
        val work = FakeWork()
        UpdateScheduler(true, { false }, work).checkNow()
        assertEquals(listOf("now"), work.calls)
    }
}
