# Contrato de mensajes (propuesto por el grupo)

> **Este contrato es una propuesta del grupo. NO es un protocolo oficial de Unitree.**
> Describe qué información intercambian la app y el robot **simulado**. Todos los valores son
> simulados o derivados, **no mediciones**.

## Cómo leer este documento

- **Los JSON de abajo están generados desde los modelos reales de la app** (`app/src/main/java/com/uade/robotcierre/model/`),
  recorriendo el simulador y el repositorio de verdad (sin azar). El test `ContractDocTest` regenera los ejemplos y
  **falla si este documento no los contiene exactamente**, así que si cambia un modelo o el simulador, el documento
  no puede quedar desactualizado en silencio.
- **Hoy la app no serializa JSON**: los mensajes viajan como objetos Kotlin entre `RobotRepository` y el ViewModel.
  El JSON es la representación propuesta del mismo contrato, por si hubiera un robot o un servidor real.
- Convenciones: las horas son **epoch en milisegundos**; las enumeraciones van como **texto** con el nombre del valor
  (`"ON_MISSION"`); un campo opcional ausente se escribe `null`. Las horas de los ejemplos son ficticias
  (2026-11-15T22:41:05Z + el tiempo simulado).

## Los cuatro mensajes

| Mensaje | Dirección | Cuándo |
|---|---|---|
| **Telemetría** | robot → app | un paquete por segundo |
| **Comando** | app → robot | cuando el operador envía una orden |
| **Respuesta** | robot → app | 1 a 2 s después de cada comando |
| **Evento** | robot → app | cuando ocurre algo que el operador debe saber |

### 1. Telemetría (`Telemetry`)

Se emite cada 1 s. Si la app pasa más de 5 s sin recibir un paquete, marca todos los datos como **antiguos**.

| Campo | Tipo | Descripción |
|---|---|---|
| `robotId` | texto | Identificador del robot (`go2-edu-01`). |
| `timestamp` | entero (ms) | Hora del paquete. |
| `sequence` | entero | Número de paquete. Solo avanza en los paquetes emitidos: durante una desconexión no se emite ninguno. |
| `connection` | enum `ConnectionState` | `CONNECTED`, `DEGRADED`, `DISCONNECTED`. |
| `batteryPercent` | entero 0–100 | Batería (valor simulado). Advertencia al 20 %; no se inicia una ronda con menos de 10 %. |
| `estimatedMinutes` | entero | Autonomía estimada (valor derivado). |
| `state` | enum `RobotState` | `IDLE`, `ON_MISSION`, `PAUSED`, `RETURNING`, `STOPPED`, `FAULT`. |
| `missionId` | texto o `null` | Código de la ronda (`M-001`). `null` si no hay ronda. |
| `checkpoint` | entero 0–6 | Punto de control actual. **0 = sin ronda.** |
| `total` | entero | Cantidad de puntos de control (siempre 6). |
| `zone` | enum `Zone` o `null` | Zona actual. `null` en la base o mientras vuelve. |
| `moving` | booleano | Si el robot se está desplazando. |
| `speedMs` | decimal | Velocidad en m/s (0,8 al caminar). |
| `activeAlerts` | entero | Cantidad de alertas sin reconocer. El detalle llega en los eventos. |
| `peopleDetected` | entero | Personas detectadas en la zona actual. |
| `zonesVerified` | entero | Zonas `FREE` o `VERIFIED_MANUALLY`. |
| `zonesTotal` | entero | Siempre 6. |
| `zonesStatus` | objeto `Zone` → `ZoneStatus` | Estado de cada una de las 6 zonas. |
| `localizationQuality` | enum | `GOOD` o `DEGRADED`. |

Zonas, en orden de recorrido: `HALL`, `CORRIDOR`, `RESTROOM_DOOR`, `PERGOLA`, `LAWN`, `ESPLANADE`.
Estados de zona: `PENDING`, `FREE`, `PERSON_FOUND`, `NOT_VERIFIABLE`, `MANUAL_REVIEW`, `VERIFIED_MANUALLY`.
**El baño siempre termina en `MANUAL_REVIEW`**: el robot no entra (privacidad).

Robot en reposo, primer paquete:

```json
{
  "robotId": "go2-edu-01",
  "timestamp": 1794782465000,
  "sequence": 1,
  "connection": "CONNECTED",
  "batteryPercent": 100,
  "estimatedMinutes": 6,
  "state": "IDLE",
  "missionId": null,
  "checkpoint": 0,
  "total": 6,
  "zone": null,
  "moving": false,
  "speedMs": 0.0,
  "activeAlerts": 0,
  "peopleDetected": 0,
  "zonesVerified": 0,
  "zonesTotal": 6,
  "zonesStatus": {
    "HALL": "PENDING",
    "CORRIDOR": "PENDING",
    "RESTROOM_DOOR": "PENDING",
    "PERGOLA": "PENDING",
    "LAWN": "PENDING",
    "ESPLANADE": "PENDING"
  },
  "localizationQuality": "GOOD"
}
```

Robot detenido a distancia por una persona en la zona techada del patio (el baño ya espera revisión manual,
hay una advertencia de batería y la alerta de la persona: `activeAlerts` es 3):

```json
{
  "robotId": "go2-edu-01",
  "timestamp": 1794782536000,
  "sequence": 72,
  "connection": "CONNECTED",
  "batteryPercent": 20,
  "estimatedMinutes": 1,
  "state": "PAUSED",
  "missionId": "M-001",
  "checkpoint": 4,
  "total": 6,
  "zone": "PERGOLA",
  "moving": false,
  "speedMs": 0.0,
  "activeAlerts": 3,
  "peopleDetected": 1,
  "zonesVerified": 2,
  "zonesTotal": 6,
  "zonesStatus": {
    "HALL": "FREE",
    "CORRIDOR": "FREE",
    "RESTROOM_DOOR": "MANUAL_REVIEW",
    "PERGOLA": "PERSON_FOUND",
    "LAWN": "PENDING",
    "ESPLANADE": "PENDING"
  },
  "localizationQuality": "GOOD"
}
```

### 2. Comando (`Command`)

| Campo | Tipo | Descripción |
|---|---|---|
| `commandId` | texto | Identificador que genera la app (`CMD-001`, `CMD-002`, …). |
| `type` | enum `CommandType` | `START_MISSION`, `PAUSE_MISSION`, `RESUME_MISSION`, `CANCEL_MISSION`, `RETURN_TO_SAFE_POINT`, `OPERATIONAL_STOP`, `ACK_ALERT`, `MANUAL_REVIEW`. |
| `issuedAt` | entero (ms) | Hora en que el operador envió la orden. |
| `operatorId` | texto | Operador (`operador-1`; no hay inicio de sesión). |
| `expiresInSeconds` | entero | Plazo para recibir la respuesta (10 s). |
| `zone` | enum `Zone` o `null` | **Solo para `MANUAL_REVIEW`**: la zona que se revisa. En los demás comandos es `null`. |

`OPERATIONAL_STOP` es un comando de **software simulado** y **no reemplaza una parada de emergencia física**.

Iniciar una ronda:

```json
{
  "commandId": "CMD-001",
  "type": "START_MISSION",
  "issuedAt": 1794782466500,
  "operatorId": "operador-1",
  "expiresInSeconds": 10,
  "zone": null
}
```

Registrar la revisión manual de la puerta del baño:

```json
{
  "commandId": "CMD-002",
  "type": "MANUAL_REVIEW",
  "issuedAt": 1794782537000,
  "operatorId": "operador-1",
  "expiresInSeconds": 10,
  "zone": "RESTROOM_DOOR"
}
```

### 3. Respuesta (`CommandResponse`)

| Campo | Tipo | Descripción |
|---|---|---|
| `commandId` | texto | El comando al que responde. |
| `status` | enum `CommandStatus` | `CONFIRMED`, `REJECTED` o `NO_RESPONSE` (ver abajo). |
| `reason` | texto o `null` | Motivo. Presente al rechazar o al vencer el plazo. |

**Enviar una orden no equivale a que se ejecutó.** Desde que la app la envía, cada orden pasa por estos estados:

| Estado | Quién lo produce |
|---|---|
| `PENDING` | La app, mientras espera. **No viaja en la respuesta**: es un estado local. |
| `CONFIRMED` | El robot: la orden se ejecutó. |
| `REJECTED` | El robot: la orden no corresponde (con su `reason`). |
| `NO_RESPONSE` | La app, **localmente**: no llegó respuesta en `expiresInSeconds` (por ejemplo, sin conexión). |

Confirmada:

```json
{
  "commandId": "CMD-001",
  "status": "CONFIRMED",
  "reason": null
}
```

Rechazada (se intentó reanudar con una alerta sin reconocer):

```json
{
  "commandId": "CMD-003",
  "status": "REJECTED",
  "reason": "Hay una alerta sin reconocer."
}
```

Sin respuesta (la detención se envió estando desconectado):

```json
{
  "commandId": "CMD-004",
  "status": "NO_RESPONSE",
  "reason": "Sin respuesta del robot en 10 s."
}
```

### 4. Evento (`RobotEvent`)

| Campo | Tipo | Descripción |
|---|---|---|
| `eventId` | texto | Identificador (`E-001`, …). |
| `type` | enum `EventType` | `PERSON_DETECTED`, `ZONE_NOT_VERIFIABLE`, `LOW_BATTERY`, `CONNECTION_LOST`, `OBSTACLE`, `MISSION_COMPLETE`. |
| `severity` | enum `Severity` | `INFO`, `WARNING`, `CRITICAL`. |
| `zone` | enum `Zone` o `null` | Zona del evento, si corresponde. |
| `details` | texto | Descripción para el operador. |
| `acknowledged` | booleano | Si ya fue reconocido. `MISSION_COMPLETE` llega ya reconocido. |

El evento **no lleva hora**: la app anota la hora de recepción (y la guarda en el historial).

Advertencia de batería baja (al cruzar el 20 %, con las zonas que faltan):

```json
{
  "eventId": "E-002",
  "type": "LOW_BATTERY",
  "severity": "WARNING",
  "zone": null,
  "details": "Batería al 20 %. Volver a la base o cancelar la ronda. Zonas sin visitar: Zona techada del patio, Césped con asientos, Explanada y rampa.",
  "acknowledged": false
}
```

Persona detectada en el patio:

```json
{
  "eventId": "E-003",
  "type": "PERSON_DETECTED",
  "severity": "CRITICAL",
  "zone": "PERGOLA",
  "details": "Persona detectada en Zona techada del patio. El robot se detuvo a distancia.",
  "acknowledged": false
}
```

## Diferencias entre el CLAUDE.md original y el código

**Todos los campos y valores que nombra el CLAUDE.md existen en el código** (lo verifica
`ContractDocTest.todosLosCamposYValoresDelClaudeMdExistenEnElCodigo`). Lo que hay de más o distinto:

| Qué | Detalle |
|---|---|
| `Telemetry.zonesStatus` | **Agregado.** Hace falta para mostrar "X de 6 con su estado". |
| `Command.zone` | **Agregado.** `MANUAL_REVIEW` necesita saber qué zona se revisa. |
| `MissionEntity.code` | **Agregado.** Guarda el código que mostró el robot (`M-001`); el `id` de Room es un número autogenerado, porque el simulador reinicia los códigos en cada arranque. |
| `Telemetry.checkpoint` | Va de **0 a 6** (0 = sin ronda); el CLAUDE.md decía 1..6. |
| `Telemetry.zone`, `missionId` | Pueden ser `null` (en la base o sin ronda). |
| `Telemetry.activeAlerts` | Es un **entero** (cantidad); el detalle viaja en los eventos. |
| Entidades de Room | En el código están en inglés: `inicio`→`startedAt`, `fin`→`endedAt`, `resultado`→`result`, `tipo`→`type`, `severidad`→`severity`, `zona`→`zone`, `detalle`→`details`, `reconocida`→`acknowledged`. |

Valores que **existen pero el simulador nunca produce**: `ConnectionState.DEGRADED`, `RobotState.FAULT` y
`EventType.OBSTACLE`. La app los sabe mostrar y la regla de "ruta bloqueada" está testeada, pero no hay forma de
provocarlos desde el panel de simulación.
