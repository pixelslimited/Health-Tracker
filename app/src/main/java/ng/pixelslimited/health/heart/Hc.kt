package ng.pixelslimited.health.heart

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.data.T
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Imports readings that watches, bands, cuffs and glucometers have synced into Health Connect. */
object Hc {
    val perms: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun available(ctx: Context): Boolean = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(ctx: Context): Boolean =
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(perms)

    /** Returns how many new readings were added from the last 30 days. */
    suspend fun sync(ctx: Context): Int {
        val client = HealthConnectClient.getOrCreate(ctx)
        val range = TimeRangeFilter.after(Instant.now().minus(30, ChronoUnit.DAYS))
        val found = ArrayList<Entry>()

        // Wearables write heart rate every few minutes; keep one average per hour.
        val hourly = HashMap<Long, MutableList<Long>>()
        val hr = client.readRecords(ReadRecordsRequest(recordType = HeartRateRecord::class, timeRangeFilter = range, ascendingOrder = false))
        for (r in hr.records) {
            for (s in r.samples) hourly.getOrPut(s.time.toEpochMilli() / 3600000L) { ArrayList() }.add(s.beatsPerMinute)
        }
        for ((hour, list) in hourly) found.add(Entry(T.HR, hour * 3600000L, list.average(), note = "HC"))

        val bp = client.readRecords(ReadRecordsRequest(recordType = BloodPressureRecord::class, timeRangeFilter = range, ascendingOrder = false))
        for (r in bp.records) {
            found.add(Entry(T.BP, r.time.toEpochMilli(), r.systolic.inMillimetersOfMercury, r.diastolic.inMillimetersOfMercury, "HC"))
        }

        val glu = client.readRecords(ReadRecordsRequest(recordType = BloodGlucoseRecord::class, timeRangeFilter = range, ascendingOrder = false))
        for (r in glu.records) {
            val ctxCode = if (r.relationToMeal == BloodGlucoseRecord.RELATION_TO_MEAL_FASTING) 0.0 else 2.0
            found.add(Entry(T.GLU, r.time.toEpochMilli(), r.level.inMilligramsPerDeciliter, ctxCode, "HC"))
        }

        val sleep = client.readRecords(ReadRecordsRequest(recordType = SleepSessionRecord::class, timeRangeFilter = range, ascendingOrder = false))
        for (r in sleep.records) {
            val hours = (r.endTime.toEpochMilli() - r.startTime.toEpochMilli()) / 3600000.0
            found.add(Entry(T.SLEEP, r.endTime.toEpochMilli(), hours, note = "HC"))
        }

        var added = 0
        Store.edit { p ->
            val have = p.entries.map { it.type to it.ts }.toSet()
            val fresh = found.filter { (it.type to it.ts) !in have }
            added = fresh.size
            p.copy(entries = p.entries + fresh)
        }
        return added
    }
}
