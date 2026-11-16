package com.uade.robotcierre.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.uade.robotcierre.model.MissionRecord
import com.uade.robotcierre.model.MissionResult

/** Ronda guardada. El id es autogenerado: el código del robot ("M-001") se repite entre sesiones. */
@Entity(tableName = "missions")
data class MissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val startedAt: Long,
    val endedAt: Long?,
    val result: MissionResult,
    val zonesVerified: Int
) {
    fun toRecord() = MissionRecord(id, code, startedAt, endedAt, result, zonesVerified)
}
