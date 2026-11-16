package com.uade.robotcierre.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.uade.robotcierre.model.EventRecord
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Zone

/** Evento guardado. [missionId] es null si ocurrió fuera de una ronda. */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = MissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["missionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("missionId")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: Long?,
    val type: EventType,
    val severity: Severity,
    val zone: Zone?,
    val details: String,
    val timestamp: Long,
    val acknowledged: Boolean
) {
    fun toRecord() =
        EventRecord(id, missionId, type, severity, zone, details, timestamp, acknowledged)
}
