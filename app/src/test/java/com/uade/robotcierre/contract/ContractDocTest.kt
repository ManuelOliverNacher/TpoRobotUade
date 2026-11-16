package com.uade.robotcierre.contract

import com.uade.robotcierre.data.EventEntity
import com.uade.robotcierre.data.MissionEntity
import com.uade.robotcierre.model.Command
import com.uade.robotcierre.model.CommandResponse
import com.uade.robotcierre.model.CommandStatus
import com.uade.robotcierre.model.CommandType
import com.uade.robotcierre.model.ConnectionState
import com.uade.robotcierre.model.EventType
import com.uade.robotcierre.model.LocalizationQuality
import com.uade.robotcierre.model.RobotEvent
import com.uade.robotcierre.model.RobotState
import com.uade.robotcierre.model.Severity
import com.uade.robotcierre.model.Telemetry
import com.uade.robotcierre.model.Zone
import com.uade.robotcierre.model.ZoneStatus
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContractDocTest {

    private val doc = File("../docs/contrato-mensajes.md")

    /** Deja los ejemplos en app/build/contract-examples/ (de ahí se arma el documento). */
    @Test
    fun losEjemplosSeGeneranYSeGuardan() = runTest {
        val examples = generateContractExamples()
        val dir = File("build/contract-examples").apply { mkdirs() }
        examples.forEach { (name, json) -> File(dir, "$name.json").writeText(json + "\n") }
        assertEquals(9, examples.size)
        examples.values.forEach { assertTrue(it.startsWith("{") && it.endsWith("}")) }
    }

    @Test
    fun losEjemplosSonReproducibles() {
        // Mismo recorrido en dos corridas independientes (cada una con su reloj virtual):
        // el simulador es determinista, así que los ejemplos salen idénticos.
        var first = emptyMap<String, String>()
        var second = emptyMap<String, String>()
        runTest { first = generateContractExamples() }
        runTest { second = generateContractExamples() }
        assertEquals(first, second)
    }

    /** Si cambia un modelo o el simulador, este test avisa que el documento quedó desactualizado. */
    @Test
    fun elDocumentoContieneExactamenteLosEjemplosGenerados() = runTest {
        assertTrue("falta ${doc.path}", doc.exists())
        val text = doc.readText()
        generateContractExamples().forEach { (name, json) ->
            assertTrue("El ejemplo '$name' del documento está desactualizado:\n$json", text.contains(json))
        }
    }

    // ------------------------------------------------- campos del CLAUDE.md original

    private fun fieldNames(c: Class<*>) =
        c.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }

    private fun constants(c: Class<out Enum<*>>) = c.enumConstants!!.map { it.name }.toSet()

    @Test
    fun todosLosCamposYValoresDelClaudeMdExistenEnElCodigo() {
        // Telemetry
        val telemetry = listOf(
            "robotId", "timestamp", "sequence", "connection", "batteryPercent", "estimatedMinutes", "state",
            "missionId", "checkpoint", "total", "zone", "moving", "speedMs", "activeAlerts", "peopleDetected",
            "zonesVerified", "zonesTotal", "localizationQuality"
        )
        assertEquals(emptyList<String>(), telemetry - fieldNames(Telemetry::class.java).toSet())
        // Command, CommandResponse, RobotEvent
        assertEquals(
            emptyList<String>(),
            listOf("commandId", "type", "issuedAt", "operatorId", "expiresInSeconds") - fieldNames(Command::class.java).toSet()
        )
        assertEquals(
            emptyList<String>(),
            listOf("commandId", "status", "reason") - fieldNames(CommandResponse::class.java).toSet()
        )
        assertEquals(
            emptyList<String>(),
            listOf("eventId", "type", "severity", "zone", "details", "acknowledged") - fieldNames(RobotEvent::class.java).toSet()
        )
        // Entidades de Room: el CLAUDE.md las nombra en español (inicio, fin, resultado, ...).
        assertEquals(
            emptyList<String>(),
            listOf("id", "startedAt", "endedAt", "result", "zonesVerified") - fieldNames(MissionEntity::class.java).toSet()
        )
        assertEquals(
            emptyList<String>(),
            listOf("id", "missionId", "type", "severity", "zone", "details", "timestamp", "acknowledged") -
                fieldNames(EventEntity::class.java).toSet()
        )
        // Enums: mismos valores que el CLAUDE.md (ni de más ni de menos).
        assertEquals(setOf("CONNECTED", "DEGRADED", "DISCONNECTED"), constants(ConnectionState::class.java))
        assertEquals(setOf("IDLE", "ON_MISSION", "PAUSED", "RETURNING", "STOPPED", "FAULT"), constants(RobotState::class.java))
        assertEquals(
            listOf("HALL", "CORRIDOR", "RESTROOM_DOOR", "PERGOLA", "LAWN", "ESPLANADE"),
            Zone.entries.map { it.name }
        )
        assertEquals(
            setOf("PENDING", "FREE", "PERSON_FOUND", "NOT_VERIFIABLE", "MANUAL_REVIEW", "VERIFIED_MANUALLY"),
            constants(ZoneStatus::class.java)
        )
        assertEquals(
            setOf(
                "START_MISSION", "PAUSE_MISSION", "RESUME_MISSION", "CANCEL_MISSION",
                "RETURN_TO_SAFE_POINT", "OPERATIONAL_STOP", "ACK_ALERT", "MANUAL_REVIEW"
            ),
            constants(CommandType::class.java)
        )
        assertEquals(setOf("PENDING", "CONFIRMED", "REJECTED", "NO_RESPONSE"), constants(CommandStatus::class.java))
        assertEquals(setOf("INFO", "WARNING", "CRITICAL"), constants(Severity::class.java))
        assertEquals(
            setOf("PERSON_DETECTED", "ZONE_NOT_VERIFIABLE", "LOW_BATTERY", "CONNECTION_LOST", "OBSTACLE", "MISSION_COMPLETE"),
            constants(EventType::class.java)
        )
        assertEquals(setOf("GOOD", "DEGRADED"), constants(LocalizationQuality::class.java))
    }

    @Test
    fun loQueHayDeMasEnElCodigoRespectoDelClaudeMd() {
        val extrasTelemetry = fieldNames(Telemetry::class.java) -
            listOf(
                "robotId", "timestamp", "sequence", "connection", "batteryPercent", "estimatedMinutes", "state",
                "missionId", "checkpoint", "total", "zone", "moving", "speedMs", "activeAlerts", "peopleDetected",
                "zonesVerified", "zonesTotal", "localizationQuality"
            ).toSet()
        assertEquals(listOf("zonesStatus"), extrasTelemetry)
        assertEquals(
            listOf("zone"),
            fieldNames(Command::class.java) - listOf("commandId", "type", "issuedAt", "operatorId", "expiresInSeconds").toSet()
        )
        assertEquals(
            listOf("code"),
            fieldNames(MissionEntity::class.java) - listOf("id", "startedAt", "endedAt", "result", "zonesVerified").toSet()
        )
    }
}
