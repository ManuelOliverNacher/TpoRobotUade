# Barrido de cierre de la planta baja de UADE

App Android para **operar y supervisar un robot Unitree Go2 EDU simulado**. Trabajo práctico de HMI/HRI de *Desarrollo de Aplicaciones I* (UADE).

**Caso de uso:** cuando termina la cursada de noche, el robot recorre la planta baja de la sede (hall, pasillo de servicios, puerta del baño, zona techada del patio, césped con asientos y explanada con rampa) para orientar a las personas hacia la salida y verificar que no quede nadie. Un operador de seguridad lo supervisa desde el celular. El robot **no entra al baño** (privacidad), **no abre puertas** y **no sube escaleras**.

> ## Qué es real y qué no
> - **No hay robot, servidor ni internet.** Todo funciona sin conexión en el celular. La app no declara el permiso `INTERNET` (verificado sobre el APK con `aapt2 dump permissions`).
> - **El robot es un simulador.** Todos los valores (batería, velocidad, minutos restantes, calidad de localización, personas detectadas) son **derivados o inventados por el grupo, no mediciones**.
> - **La "detención operativa" es un comando de software simulado.** **No reemplaza una parada de emergencia física**, y la app lo dice en pantalla.
> - **El contrato de datos** (telemetría, órdenes, eventos) es una **propuesta del grupo, no un protocolo oficial de Unitree**.
> - Enviar una orden **no equivale a que se ejecutó**: cada orden muestra su estado (Pendiente, Confirmada, Rechazada o Sin respuesta).

## Instalación

Requisitos: un celular o emulador con **Android 11 o superior (API 30+)**.

El APK ya compilado está en [`entrega/BarridoCierreUADE-debug.apk`](entrega/BarridoCierreUADE-debug.apk) (30,9 MB, firmado con la clave de depuración).

**Con `adb`** (celular con depuración USB, o emulador):

```bash
adb install -r entrega/BarridoCierreUADE-debug.apk
```

**Sin `adb`:** copiá el APK al celular, abrilo desde el administrador de archivos y aceptá "instalar apps de orígenes desconocidos" para esa app.

**Compilarlo desde el código** (JDK 17 o superior; Android Studio lo trae):

```bash
./gradlew :app:assembleDebug
# resultado: app/build/outputs/apk/debug/app-debug.apk
```

## Cómo se usa

1. **Selección de robot:** tocá **Supervisar**. La app trae un único robot simulado (`go2-edu-01`).
2. **Panel:** muestra la telemetría (conexión, batería, estado, progreso, movimiento, personas, zonas verificadas, localización) y los botones de control. Los botones que no corresponden aparecen **deshabilitados con el motivo**.
3. **Rondas:** la ronda actual, las 6 zonas en orden y, al terminar, el **resumen de cierre** con "Registrar revisión manual".
4. **Alertas:** las abiertas arriba y las cerradas atenuadas. "Reconocer alertas" cierra todas las abiertas.
5. **Historial:** las rondas guardadas (sobreviven al cierre de la app); tocá una para ver sus eventos.
6. **Cancelar ronda** y **Detención operativa** piden confirmación antes de enviarse.

Si pasan más de 5 s sin telemetría, todo se marca como **ANTIGUO** y se muestra la hora de la última actualización.

La app está bloqueada en vertical. Está pensada para el ancho de 360 dp de la especificación (probada también a 411 dp).

## Panel de simulación: los 5 escenarios

El panel sirve para **forzar** situaciones en el simulador. Se abre con el **engranaje ⚙** de la barra azul (está en las cuatro pestañas) y solo existe en modo demo. Las acciones que no tendrían efecto en ese momento aparecen deshabilitadas con su motivo. Al elegir una, la hoja se cierra.

Tiempos del simulador (valores propuestos por el grupo): cada zona dura **20 s** (15 s de traslado + 5 s de verificación), el regreso a la base **15 s**, así que una ronda completa dura **unos 2 min 15 s** y gasta **~34 % de batería**.

### 1. Misión normal
1. Panel → **Iniciar ronda** (la orden pasa de *Pendiente* a *Confirmada*).
2. Mirá la pestaña **Rondas**: el robot avanza del punto 1 al 6 y vuelve a la base.
3. Al terminar aparece el **resumen de cierre**: "Ronda completa. Zonas libres: 5 de 6." La **puerta del baño siempre queda en revisión manual**.
4. Tocá **Registrar revisión manual**: pasa a "Verificada manualmente" (6 de 6).
5. En **Historial** queda la ronda con sus eventos.

### 2. Batería baja
1. Con una ronda en curso: ⚙ → **Batería baja** (lleva la batería a 21 %).
2. A los ~4 s, al cruzar el 20 %, aparece la alerta **Batería baja** (pestaña Alertas) con las **zonas sin visitar**.
3. La propia alerta ofrece las dos acciones: **Volver a la base** y **Cancelar ronda** (esta pide confirmación). Son las mismas órdenes del Panel y quedan deshabilitadas, con su motivo, si ya no hay ronda en curso. En el Historial la ronda queda como *Volvió antes de terminar* o *Cancelada*.

> Una ronda normal desde 100 % no llega al 20 %: la advertencia solo aparece con esta acción (o después de varias rondas seguidas).

### 3. Desconexión
1. Con una ronda en curso: ⚙ → **Forzar desconexión**. Aparece el **banner rojo** "Sin conexión con el robot. Última actualización …" y la ronda queda en *Pausado*.
2. A los 5 s: banner **Datos antiguos** y todo con la etiqueta **ANTIGUO**. Los botones quedan deshabilitados ("Sin conexión con el robot."), salvo **Detención operativa**, que advierte "Puede quedar sin respuesta."
3. Probá **Detención operativa** → *Confirmar*: la orden queda *Pendiente* y a los 10 s pasa a **Sin respuesta**.
4. ⚙ → **Reconectar**: banner verde **"Conexión recuperada. Reanudá la ronda cuando estés listo."** La ronda sigue pausada hasta que el operador toque **Reanudar ronda**.

### 4. Incidente: persona detectada en el patio
1. Con una ronda en curso, esperá a **"Punto 4 de 6 · Zona techada del patio"** (aparece ~60 s después de iniciar). La acción actúa sobre la zona **donde está el robot** en ese momento.
2. ⚙ → **Persona detectada**: el robot **se detiene a distancia** (*Pausado*), aparece la alerta crítica y la zona queda en **Persona encontrada**.
3. Pestaña **Alertas**: lee "Elegí: avisar a seguridad, continuar o volver a la base." Tocá **Reconocer alertas**.
4. En el Panel: **Reanudar ronda** (continuar) o **Volver a la base**. Avisar a seguridad es una decisión del operador **fuera de la app**: no hay un botón para eso.
5. Reconocer la alerta no cambia el estado de la zona: sigue en *Persona encontrada*.

### 5. Zona no verificable (escenario propio)
1. Con una ronda en curso, en una zona **que no sea el baño** (por ejemplo "Punto 5 · Césped con asientos"): ⚙ → **Zona no verificable**.
2. La **localización pasa a Degradada** (simula lluvia), aparece la alerta y la zona queda **No verificable**. La localización vuelve a *Buena* al pasar a la zona siguiente.
3. Al terminar, el resumen de cierre pide la revisión manual de esa zona (y del baño). Tocá **Registrar revisión manual** por cada una (la orden es `MANUAL_REVIEW`).

### Extra: rechazo de una orden
⚙ → **Rechazar próxima orden**: aparece un aviso breve ("La próxima orden será rechazada.") y la hoja se cierra. Enviá cualquier orden (por ejemplo **Iniciar ronda**): queda **Rechazada** con su motivo. Solo afecta a la siguiente orden.

### Reiniciar la demo
- Cerrar la app descarta el robot simulado: al volver a abrirla hay un robot nuevo, en reposo. Si había una ronda abierta, queda en el historial como *Interrumpida*.
- Para vaciar también el historial: `adb shell pm clear com.uade.robotcierre` (o *Ajustes → Apps → Borrar datos*).
- Para publicar la app **sin** el panel de simulación: crear el contenedor con `AppContainer(this, demoMode = false)` en `RobotCierreApplication`.

## Arquitectura

```
UI (Jetpack Compose, Material 3)
   │  observa StateFlow<UiState>  ·  envía RobotUiEvent
   ▼
RobotViewModel ── reglas de habilitación (CommandRules) ── HistoryRecorder
   │                                                           │
   ▼                                                           ▼
RobotRepository (interfaz)                          HistoryRepository (interfaz)
   │                                                           │
   ▼                                                           ▼
SimulatedRobotRepository  →  SimulationEngine       RoomHistoryRepository  →  Room (SQLite)
```

| Paquete | Contenido |
|---|---|
| `model/` | Enums y data classes del contrato: `Telemetry`, `Command`, `CommandResponse`, `RobotEvent`, `Zone`, `ZoneStatus`, registros del historial |
| `repository/` | Interfaces `RobotRepository`, `SimulationControls`, `HistoryRepository` y el simulador (`SimulatedRobotRepository` + `SimulationEngine`, determinista y sin azar) |
| `viewmodel/` | `RobotViewModel`, `UiState`, `CommandRules`, `HistoryRecorder` |
| `data/` | Room: `MissionEntity`, `EventEntity`, `HistoryDao`, `AppDatabase`, `RoomHistoryRepository` |
| `ui/` | Pantallas (`screens/`), componentes reutilizables (`components/`), navegación (`navigation/`) y tema (`theme/`) |

- **La UI solo observa estado y envía eventos al ViewModel**; nunca toca el simulador.
- Las reglas de habilitación de comandos las evalúa el ViewModel, no la UI.
- Inyección de dependencias **manual** (`AppContainer`), sin Hilt. Corrutinas y Flow; sin librerías de red.
- El ViewModel se crea **una sola vez por Activity** y sobrevive a la recreación. El simulador vive y muere con el ViewModel.
- Versiones: Kotlin 2.2.10, AGP 9.3.3, Compose BOM 2026.02.01, Room 2.8.5 (con KSP 2.3.14), corrutinas 1.10.2, `minSdk 30`, `targetSdk 37`.

## Tests

```bash
# Unitarios (sin dispositivo, ~10 s): reglas de habilitación, simulador, ViewModel, historial
./gradlew :app:testDebugUnitTest

# Instrumentados (necesitan un emulador o celular conectado, ~5 min): Room, pantallas y flujos completos
./gradlew :app:connectedDebugAndroidTest
```

Estado actual: **100 tests unitarios y 71 instrumentados**, todos en verde (probados en un emulador Pixel 7 con Android 17). Los reportes HTML quedan en `app/build/reports/tests/testDebugUnitTest/index.html` y `app/build/reports/androidTests/connected/debug/index.html`.

> **Cuidado:** los tests instrumentados **borran el historial** de la app instalada en el dispositivo de prueba y la **desinstalan** al terminar. Correrlos en un emulador, no en un celular con datos que quieras conservar.

Algunos tests aceleran el simulador (una ronda de ~20 s) para recorrer los escenarios completos de punta a punta.

**Regenerar las capturas** (se guardan en la carpeta privada de la app; hay que usar `am instrument` porque Gradle desinstala la app al terminar):

```bash
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class com.uade.robotcierre.SimulacionCapturasTest \
    com.uade.robotcierre.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as com.uade.robotcierre cat files/capturas/<nombre>.png > <nombre>.png
```

Clases de capturas: `PanelCapturasTest`, `PantallasCapturasTest` y `SimulacionCapturasTest`.

## Qué hay en el repositorio

| Ruta | Qué es |
|---|---|
| `app/` | Código fuente de la app, tests y el esquema de Room exportado (`app/schemas/`) |
| `entrega/` | APK instalable |
| `evidencias/` | Capturas del emulador: selección, panel, rondas, alertas, historial, simulación y los escenarios; incluye el Panel a 360 dp |
| `docs/contrato-mensajes.md` | Contrato de mensajes (telemetría, comando, respuesta, evento) con ejemplos JSON **generados desde los modelos reales**; un test falla si el documento se desactualiza |
| `ESPECIFICACION_PANTALLAS.md` | Especificación visual y de pantallas (base del Figma) |
| `CLAUDE.md` | Reglas y alcance del proyecto |

## Limitaciones conocidas

- Solo se probó en un **emulador** (Pixel 7, Android 17), a 411 dp y a 360 dp. No se probó en un celular físico ni en una tablet.
- El tema es **solo claro** (la especificación define una única paleta) y la orientación está **fija en vertical**. Desde Android 16, en pantallas grandes (tablets) el sistema puede ignorar el bloqueo de orientación.
- El simulador no genera nunca el estado *Falla*, ni la conexión *Degradada*, ni el evento *Obstáculo* (la regla de "ruta bloqueada" existe y está testeada, pero no se puede provocar desde el panel).
- No hay botón de **avisar a seguridad**: es una decisión del operador fuera de la app.
- **Reconocer alertas** cierra todas las abiertas a la vez; el contrato no permite elegir una.
- Los códigos de ronda (`M-001`) los reinicia el simulador en cada arranque, así que el historial puede mostrar varias rondas con el mismo código; se distinguen por fecha.
- Solo hay APK de **depuración** (sin firma de publicación ni ofuscación).
