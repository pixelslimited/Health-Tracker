package ng.pixelslimited.health.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object T {
    const val HR = "HR"
    const val BP = "BP"
    const val GLU = "GLU"
    const val WEIGHT = "WEIGHT"
    const val SLEEP = "SLEEP"
    const val WATER = "WATER"
    const val MEAL = "MEAL"
    const val HBA1C = "HBA1C"
    /** Finished workout: a=drills done, b=category (0 flat stomach, 1 body building). */
    const val WORK = "WORK"
}

/** One logged reading. BP: a=systolic, b=diastolic. GLU: a=mg/dL, b=0 fasting, 1 after meal, 2 random. */
data class Entry(val type: String, val ts: Long, val a: Double, val b: Double = 0.0, val note: String = "") {
    fun json(): JSONObject = JSONObject().put("t", type).put("ts", ts).put("a", a).put("b", b).put("n", note)

    companion object {
        fun of(o: JSONObject) = Entry(o.getString("t"), o.getLong("ts"), o.getDouble("a"), o.optDouble("b", 0.0), o.optString("n", ""))
    }
}

data class Reminder(val id: Int, val label: String, val hour: Int, val minute: Int, val on: Boolean = true) {
    fun json(): JSONObject = JSONObject().put("id", id).put("l", label).put("h", hour).put("m", minute).put("on", on)

    companion object {
        fun of(o: JSONObject) = Reminder(o.getInt("id"), o.getString("l"), o.getInt("h"), o.getInt("m"), o.optBoolean("on", true))
    }
}

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Me",
    val birthYear: Int = 1990,
    val heightCm: Int = 170,
    val male: Boolean = true,
    val dailyGoal: Int = 8000,
    val weeklyGoal: Int = 50000,
    val waterGoal: Int = 2000,
    val fastingMax: Int = 130,
    val hba1cTarget: Int = 70,
    val contactName: String = "",
    val contactPhone: String = "",
    val sleepGoal: Int = 8,
    val sleepStart: Long = 0,
    val entries: List<Entry> = emptyList(),
    val steps: Map<String, Int> = emptyMap(),
    val hourly: Map<String, List<Int>> = emptyMap(),
    val reminders: List<Reminder> = emptyList(),
) {
    fun json(): JSONObject {
        val st = JSONObject()
        steps.forEach { (k, v) -> st.put(k, v) }
        val hr = JSONObject()
        hourly.forEach { (k, v) -> hr.put(k, JSONArray(v)) }
        return JSONObject().put("id", id).put("name", name).put("birthYear", birthYear).put("heightCm", heightCm)
            .put("male", male).put("dailyGoal", dailyGoal).put("weeklyGoal", weeklyGoal).put("waterGoal", waterGoal)
            .put("fastingMax", fastingMax).put("hba1cTarget", hba1cTarget).put("contactName", contactName)
            .put("contactPhone", contactPhone).put("sleepGoal", sleepGoal).put("sleepStart", sleepStart).put("steps", st).put("hourly", hr)
            .put("entries", JSONArray(entries.map { it.json() }))
            .put("reminders", JSONArray(reminders.map { it.json() }))
    }

    companion object {
        fun of(o: JSONObject): Profile {
            val d = Profile()
            val st = o.optJSONObject("steps") ?: JSONObject()
            val steps = HashMap<String, Int>()
            st.keys().forEach { steps[it] = st.getInt(it) }
            val ho = o.optJSONObject("hourly") ?: JSONObject()
            val hourly = HashMap<String, List<Int>>()
            ho.keys().forEach { k ->
                val a = ho.getJSONArray(k)
                hourly[k] = (0 until a.length()).map { a.getInt(it) }
            }
            val ea = o.optJSONArray("entries") ?: JSONArray()
            val ra = o.optJSONArray("reminders") ?: JSONArray()
            return Profile(
                id = o.getString("id"),
                name = o.optString("name", d.name),
                birthYear = o.optInt("birthYear", d.birthYear),
                heightCm = o.optInt("heightCm", d.heightCm),
                male = o.optBoolean("male", true),
                dailyGoal = o.optInt("dailyGoal", d.dailyGoal),
                weeklyGoal = o.optInt("weeklyGoal", d.weeklyGoal),
                waterGoal = o.optInt("waterGoal", d.waterGoal),
                fastingMax = o.optInt("fastingMax", d.fastingMax),
                hba1cTarget = o.optInt("hba1cTarget", d.hba1cTarget),
                contactName = o.optString("contactName", ""),
                contactPhone = o.optString("contactPhone", ""),
                sleepGoal = o.optInt("sleepGoal", d.sleepGoal),
                sleepStart = o.optLong("sleepStart", 0),
                entries = (0 until ea.length()).map { Entry.of(ea.getJSONObject(it)) },
                steps = steps,
                hourly = hourly,
                reminders = (0 until ra.length()).map { Reminder.of(ra.getJSONObject(it)) },
            )
        }
    }
}

/** theme: 0 follows the phone, 1 light, 2 dark. */
data class AppData(val active: String, val profiles: List<Profile>, val lock: Boolean = false, val theme: Int = 0) {
    val me: Profile get() = profiles.firstOrNull { it.id == active } ?: profiles.first()

    fun json(): JSONObject = JSONObject().put("active", active).put("lock", lock).put("theme", theme).put("profiles", JSONArray(profiles.map { it.json() }))

    companion object {
        fun fresh(): AppData {
            val p = Profile()
            return AppData(p.id, listOf(p))
        }

        fun of(o: JSONObject): AppData {
            val pa = o.getJSONArray("profiles")
            val profiles = (0 until pa.length()).map { Profile.of(pa.getJSONObject(it)) }
            return if (profiles.isEmpty()) fresh() else AppData(o.optString("active", profiles.first().id), profiles, o.optBoolean("lock", false), o.optInt("theme", 0))
        }
    }
}
