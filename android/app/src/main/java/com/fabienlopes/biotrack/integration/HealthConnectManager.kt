package com.fabienlopes.biotrack.integration

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import kotlin.reflect.KClass

class HealthConnectManager(private val context: Context) {
    private val zone = ZoneId.systemDefault()

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
    )

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.PROVIDER_UPDATE_REQUIRED
        else -> Availability.NOT_SUPPORTED
    }

    fun clientOrNull(): HealthConnectClient? =
        if (availability() == Availability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    suspend fun hasAllPermissions(): Boolean {
        val client = clientOrNull() ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun revokeAllPermissions(): Boolean {
        val client = clientOrNull() ?: return false
        client.permissionController.revokeAllPermissions()
        return true
    }

    fun permissionsSettingsIntent(): Intent = if (Build.VERSION.SDK_INT >= 34) {
        Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
    } else {
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            .setPackage("com.google.android.apps.healthdata")
    }

    suspend fun readDailyValues(days: Int = 30): Map<String, Map<Long, Double>> {
        val client = clientOrNull() ?: return emptyMap()
        val end = Instant.now()
        val start = end.minusSeconds(days.coerceIn(1, 365).toLong() * 86_400)
        val result = mutableMapOf<String, MutableMap<Long, Double>>()

        for (offset in 0 until days.coerceIn(1, 365)) {
            val localDay = end.atZone(zone).toLocalDate().minusDays(offset.toLong())
            val dayStart = localDay.atStartOfDay(zone).toInstant()
            val dayEnd = localDay.plusDays(1).atStartOfDay(zone).toInstant()
            val filter = TimeRangeFilter.between(maxOf(start, dayStart), minOf(end, dayEnd))

            runCatching {
                val aggregate = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), filter))
                aggregate[StepsRecord.COUNT_TOTAL]?.toDouble()?.let { value -> result.getOrPut("Pas") { mutableMapOf() }[dayStart.toEpochMilli()] = value }
            }
            runCatching {
                val weights = client.readRecords(ReadRecordsRequest(WeightRecord::class, filter)).records
                weights.map { it.weight.inKilograms }.averageOrNull()?.let { value -> result.getOrPut("Poids") { mutableMapOf() }[dayStart.toEpochMilli()] = value }
            }
            runCatching {
                val resting = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, filter)).records
                resting.map { it.beatsPerMinute.toDouble() }.averageOrNull()?.let { value -> result.getOrPut("FC au repos") { mutableMapOf() }[dayStart.toEpochMilli()] = value }
            }
            runCatching {
                val hrv = client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, filter)).records
                hrv.map { it.heartRateVariabilityMillis }.averageOrNull()?.let { value -> result.getOrPut("HRV (RMSSD)") { mutableMapOf() }[dayStart.toEpochMilli()] = value }
            }
            runCatching {
                val sleepMinutes = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter)).records.sumOf { session ->
                    overlapMinutes(session.startTime, session.endTime, dayStart, dayEnd)
                }
                if (sleepMinutes > 0) result.getOrPut("Durée du sommeil") { mutableMapOf() }[dayStart.toEpochMilli()] = sleepMinutes
            }
        }
        return result
    }

    enum class Availability { AVAILABLE, PROVIDER_UPDATE_REQUIRED, NOT_SUPPORTED }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}

internal fun overlapMinutes(start: Instant, end: Instant, windowStart: Instant, windowEnd: Instant): Double {
    val clippedStart = maxOf(start, windowStart)
    val clippedEnd = minOf(end, windowEnd)
    if (clippedEnd <= clippedStart) return 0.0
    return java.time.Duration.between(clippedStart, clippedEnd).toMillis() / 60_000.0
}
