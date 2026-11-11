package ng.pixelslimited.health.steps

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import ng.pixelslimited.health.data.Store

/**
 * Safety net: every ~15 minutes, even if the phone stopped the step service, read the hardware counter
 * and add the missed steps, then try to bring the service back.
 */
class StepJob : JobService() {
    override fun onStartJob(p: JobParameters): Boolean {
        val ctx = applicationContext
        Store.init(ctx)
        StepSync.catchUp(ctx) {
            Store.flush()
            jobFinished(p, false)
        }
        StepService.start(ctx)
        return true
    }

    override fun onStopJob(p: JobParameters) = false

    companion object {
        private const val ID = 7301

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(ID) != null) return
            js.schedule(
                JobInfo.Builder(ID, ComponentName(ctx, StepJob::class.java))
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build()
            )
        }
    }
}
