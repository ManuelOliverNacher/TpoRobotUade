# CLAUDE.md — TP HMI/HRI "Barrido de cierre de la planta baja de UADE" (Unitree Go2 EDU)

## Qué es este proyecto

App Android (APK instalable) para operar y supervisar un robot simulado Unitree Go2 EDU. Materia: Desarrollo de Aplicaciones I (UADE). Entrega final: viernes 20/11/2026. No hay robot, servidor ni internet. Todo funciona offline en el celular.

**Caso de uso:** cuando termina la cursada de noche, el robot recorre la planta baja de la sede (hall, pasillo de servicios, puerta del baño, zona techada del patio, césped con asientos, explanada y rampa) para orientar a las personas hacia la salida y verificar que no quede nadie. Un operador de seguridad lo supervisa desde una tablet/celular. El robot NO entra al baño (privacidad), NO abre puertas y NO sube escaleras.

## Reglas obligatorias del enunciado

- Kotlin + Jetpack Compose (Material 3) + MVVM + ViewModel + StateFlow.
- La UI SOLO observa estado y envía eventos al ViewModel. Nunca accede al simulador.
- Un `RobotRepository` (interfaz) desacopla telemetría, comandos y eventos de la implementación simulada.
- Paquetes separados: `model/`, `repository/`, `data/` (Room), `viewmodel/`, `ui/` (pantallas y componentes).
- Persistir en Room el historial de rondas y eventos.
- Sin permiso de INTERNET en el manifest.
- La "detención operativa" es un comando de SOFTWARE simulado. La UI debe decir de forma visible que NO reemplaza una parada de emergencia física.
- Enviar una orden NO equivale a que se ejecutó: mostrar siempre el estado de la orden.
- No presentar el JSON como protocolo oficial de Unitree: es un contrato propuesto por el grupo.

## Arquitectura

UI (Compose) → ViewModel (`StateFlow<UiState>`) → `RobotRepository` (interfaz) → `SimulatedRobotRepository` (simulador) + Room (historial).

- Eventos de la UI van al ViewModel; el estado vuelve como StateFlow.
- Inyección de dependencias MANUAL (sin Hilt) para mantenerlo simple: un `AppContainer` en `Application`.
- Corrutinas + Flow. Sin librerías de red.

## Modelo de datos (carpeta `model/`)

- `ConnectionState`: CONNECTED, DEGRADED, DISCONNECTED
- `RobotState`: IDLE, ON_MISSION, PAUSED, RETURNING, STOPPED, FAULT
- `Zone`: HALL, CORRIDOR, RESTROOM_DOOR, PERGOLA, LAWN, ESPLANADE (los 6 puntos de control, en ese orden)
- `ZoneStatus`: PENDING, FREE, PERSON_FOUND, NOT_VERIFIABLE, MANUAL_REVIEW, VERIFIED_MANUALLY
- `Telemetry`: robotId, timestamp, sequence, connection, batteryPercent, estimatedMinutes, state, missionId, checkpoint (1..6), total (6), zone, moving, speedMs, activeAlerts, peopleDetected, zonesVerified, zonesTotal, localizationQuality (GOOD/DEGRADED)
- `CommandType`: START_MISSION, PAUSE_MISSION, RESUME_MISSION, CANCEL_MISSION, RETURN_TO_SAFE_POINT, OPERATIONAL_STOP, ACK_ALERT, MANUAL_REVIEW
- `CommandStatus`: PENDING, CONFIRMED, REJECTED, NO_RESPONSE
- `Command` (commandId, type, issuedAt, operatorId, expiresInSeconds) y `CommandResponse` (commandId, status, reason)
- `RobotEvent` (eventId, type, severity INFO/WARNING/CRITICAL, zone, details, acknowledged). Tipos: PERSON_DETECTED, ZONE_NOT_VERIFIABLE, LOW_BATTERY, CONNECTION_LOST, OBSTACLE, MISSION_COMPLETE

## Telemetría que la UI debe mostrar

- **Obligatoria:** conectividad, batería, estado operativo, misión/progreso, ubicación, movimiento, alertas, última actualización.
- **Propias del servicio:** personas detectadas, zonas verificadas (X de 6 con su estado) y calidad de localización.
- **Datos antiguos:** si pasan más de 5 s sin telemetría nueva, marcar todo como "antiguo" (no presentarlo como estado actual) y mostrar la hora de la última actualización.

## Comandos: reglas de habilitación (las evalúa el ViewModel, no la UI)

| Comando | Habilitado cuando | Rechazo posible |
|---|---|---|
| START_MISSION | CONNECTED, batería >= 10 %, sin alertas críticas, estado IDLE | batería baja o ya en misión |
| PAUSE_MISSION | hay ronda en curso | sin ronda activa |
| RESUME_MISSION | ronda pausada y hay conexión | conexión perdida o alerta sin reconocer |
| CANCEL_MISSION | ronda en curso o pausada (pide confirmación) | ronda ya finalizada |
| RETURN_TO_SAFE_POINT | conectado y ruta libre | ruta bloqueada |
| OPERATIONAL_STOP | siempre visible (pide confirmación) | sin conexión: queda NO_RESPONSE |
| ACK_ALERT | hay alerta activa | alerta ya cerrada |
| MANUAL_REVIEW | zona a verificar manualmente (baño o no verificable) | zona ya verificada |

- Los botones no válidos se ven DESHABILITADOS (con el motivo).
- Cada orden pasa por PENDING → CONFIRMED / REJECTED / NO_RESPONSE y el estado se ve en pantalla.
- La respuesta simulada tarda 1–2 s; si no llega en `expiresInSeconds` (10 s) pasa a NO_RESPONSE.

## Simulador (`SimulatedRobotRepository`)

- Emite telemetría cada 1 s con Flow.
- La ronda avanza por los 6 puntos en orden y vuelve a la base; el estado es coherente con la misión.
- Batería baja de forma acelerada para la demo (una ronda completa en ~2–3 minutos).
- Umbrales (propuestos por el grupo): advertencia al 20 %, no se puede iniciar ronda < 10 %.
- Panel de simulación (visible solo en modo demo) con botones para forzar: desconexión / reconexión, rechazo de la próxima orden, batería baja, persona detectada, zona no verificable. Sirve para demostrar los 5 escenarios en el video.
- Valores derivados/inventados, NO mediciones. Decirlo en la documentación.
- Generación determinista (sin azar) donde se pueda, para que los escenarios sean reproducibles.

## Los 5 escenarios que la app debe poder demostrar

1. **Misión normal:** inicio, avance por los 6 puntos, zonas libres, regreso y resumen de cierre.
2. **Batería baja:** advertencia al 20 % con acciones (volver a la base o cancelar); si quedan zonas sin visitar, informar cuáles faltan.
3. **Desconexión:** datos marcados como antiguos, órdenes sin confirmar, recuperación al volver.
4. **Incidente:** persona detectada en el patio; el robot se detiene a distancia, alerta, y el operador decide (avisar a seguridad, continuar o volver a la base).
5. **Propio, zona no verificable:** localización degradada/lluvia; la zona queda NOT_VERIFIABLE; el operador registra revisión manual (comando MANUAL_REVIEW). El baño SIEMPRE requiere revisión manual.

## Pantallas (deben coincidir con el Figma)

Selección de robot · Panel de supervisión (telemetría + control) · Rondas/misión · Alertas · Historial (Room) · Desconexión. Más un modal de confirmación para cancelar/detener.

- Estados visuales de componentes: normal, deshabilitado, advertencia, error.
- Textos en español. Botones grandes, legibles, uso con una mano.

## Room

Entidades mínimas: `MissionEntity` (id, inicio, fin, resultado, zonas verificadas) y `EventEntity` (id, missionId, tipo, severidad, zona, detalle, timestamp, reconocida). El historial se muestra en la pantalla Historial y sobrevive al cierre de la app.

## Cómo trabajar

- Trabajar en pasos chicos y compilables. Después de cada paso: compilar y correr en el emulador.
- Orden sugerido: 1) modelos, 2) interfaz `RobotRepository`, 3) simulador, 4) ViewModel con reglas de habilitación, 5) Room, 6) pantallas en Compose, 7) panel de simulación, 8) pulido.
- Antes de escribir mucho código, proponer el plan del paso y esperar mi OK.
- Escribir tests unitarios para las reglas de habilitación y para el simulador.
- No agregar dependencias sin avisar. No usar Hilt, Retrofit ni nada de red.
- Comentarios y nombres de UI en español; identificadores de código en inglés.
- Al terminar cada paso, resumir en 3 líneas qué quedó hecho y qué sigue.

## Entrega final (para no olvidarse)

APK instalable + código fuente + Figma + diagrama de arquitectura + ejemplos JSON + evidencias (capturas) de los 5 escenarios + registro de decisiones y limitaciones + video de demo.
