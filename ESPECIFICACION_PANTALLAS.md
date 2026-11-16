# Especificación de pantallas — Barrido de cierre UADE (Go2 EDU)

Sirve para dos cosas: armar el Figma y que Claude Code construya las pantallas del APK (paso 6). Todo en español. Tamaño de frame en Figma: **360 × 800** (Android). Un solo operador, una mano.

## 1. Sistema visual

### Colores

| Uso | Color |
|---|---|
| Fondo | `#F5F7FA` |
| Superficie (tarjetas) | `#FFFFFF` |
| Texto | `#0B1D2B` |
| Texto secundario | `#5B6770` |
| Primario (barra, botones) | `#1E4E79` |
| Marino oscuro | `#0B1D2B` |
| OK / libre / conectado | `#15803D` |
| Advertencia | `#B45309` (fondo `#FEF3C7`) |
| Error / crítico / detención | `#B91C1C` (fondo `#FEE2E2`) |
| Info | `#0369A1` (fondo `#E0F2FE`) |
| Deshabilitado | texto `#9AA5AF`, fondo `#E5E9ED` |

**Dato antiguo:** igual que el normal, pero con opacidad 50 % y etiqueta "ANTIGUO".

### Tipografía (Roboto o Inter)

- Título: 22/28 negrita
- Subtítulo: 16/22 semibold
- Cuerpo: 14/20
- Dato grande: 28/32 negrita
- Etiqueta: 12/16 mayúsculas

Espaciado: múltiplos de 8. Botones de 52 px de alto, esquinas de 12. Tarjetas con borde de 1 px `#D9E0E6`.

### Componentes reutilizables

Cada uno con sus 4 estados: normal, deshabilitado, advertencia, error.

- **BotonComando:** icono + texto; deshabilitado muestra debajo el motivo en 12 px.
- **ChipEstado:** conexión, estado de orden, estado de zona.
- **TarjetaDato:** etiqueta + valor + hora de actualización.
- **BarraBateria:** verde > 20 %, ámbar ≤ 20 %, rojo < 10 %.
- **ItemAlerta:** icono de severidad, tipo, zona, hora, botón Reconocer.
- **ItemOrden:** comando, hora, chip de estado.
- **FilaZona:** número, nombre, chip de estado.
- **BannerAviso:** info / advertencia / error.
- **ModalConfirmacion.**

**Chips de estado de orden:** Pendiente (gris, con spinner) · Confirmada (verde) · Rechazada (rojo, con motivo) · Sin respuesta (ámbar).

**Chips de estado de zona:** Pendiente · Libre · Persona encontrada · No verificable · Revisión manual · Verificada manualmente.

## 2. Mapa de navegación

Selección de robot → Panel de supervisión (pantalla principal).

- Barra inferior de 4 pestañas: **Panel · Rondas · Alertas · Historial**. La pestaña Alertas muestra un globo con la cantidad de alertas abiertas.
- Desde cualquier pestaña: icono "Simulación" (solo demo) → hoja inferior con el panel de simulación.
- **Desconexión no es una pestaña:** es un estado que cubre el Panel con un banner rojo fijo y marca todo como antiguo.
- **Modal de confirmación:** aparece al pedir Cancelar ronda o Detención operativa.

## 3. Pantallas

### 3.1 Selección de robot

- Título "Elegí un robot".
- Tarjeta: "Unitree Go2 EDU · go2-edu-01", sede "Campus Urbano · planta baja", chip de conexión, batería, etiqueta "SIMULADO".
- Botón primario "Supervisar".
- Texto pequeño: "El robot es simulado. No hay hardware conectado."

### 3.2 Panel de supervisión (telemetría + control)

De arriba hacia abajo:

1. **Barra:** "go2-edu-01" · chip de conexión (Conectado / Degradada / Desconectado) · "Actualizado 22:41:05".
2. **BannerAviso** si hay datos antiguos: "Datos antiguos (más de 5 s). No representan el estado actual."
3. **Tarjeta Estado:** estado operativo (Reposo / En ronda / Pausado / Volviendo / Detenido / Falla) y progreso "Punto 3 de 6 · Zona techada" con barra de 6 segmentos.
4. **Fila de tarjetas:** Batería (barra + % + minutos estimados) · Movimiento (Caminando 0,8 m/s / Detenido).
5. **Fila de tarjetas propias:** Personas detectadas (número) · Zonas verificadas ("3 de 6") · Localización (Buena / Degradada).
6. Si hay alertas abiertas: **BannerAviso** con la más reciente y botón "Ver".
7. **Control (grilla de botones):** Iniciar ronda · Pausar / Reanudar · Cancelar ronda · Volver a la base · Revisión manual · Detención operativa (rojo, ancho completo).
8. Debajo de Detención, siempre visible: "Es un comando de software simulado. No reemplaza una parada de emergencia física."
9. Los botones no válidos aparecen deshabilitados con el motivo (por ejemplo "Batería baja", "Esperando respuesta…").
10. **Órdenes recientes:** últimas 5 con ItemOrden y su estado.

### 3.3 Rondas

- Esquema simple del recorrido en vertical: 6 FilaZona en orden (Hall e ingreso, Pasillo de servicios, Puerta del baño, Zona techada, Césped con asientos, Explanada y rampa) con su estado y una marca en la zona actual.
- Tarjeta de ronda actual: id (M-001), inicio, progreso.
- **Resumen de cierre** (al terminar): "Ronda completa. Zonas libres: 5 de 6. Puerta del baño: requiere revisión manual." con botón "Registrar revisión manual".
- Si la ronda queda incompleta por batería: lista de zonas pendientes.

### 3.4 Alertas

- Lista de ItemAlerta, más nueva primero. Abiertas arriba, cerradas atenuadas.
- Tipos: Persona detectada (crítica) · Zona no verificable (advertencia) · Batería baja (advertencia) · Conexión perdida (crítica) · Obstáculo · Ronda completa (info).
- Botón "Reconocer alertas" arriba.
- Persona detectada, texto de ejemplo: "Persona detectada en Zona techada. El robot se detuvo a distancia. Elegí: avisar a seguridad, continuar o volver a la base."

### 3.5 Historial

- Lista de rondas guardadas (id, fecha, resultado, zonas verificadas) y debajo los eventos de cada una.
- Estado vacío: "Todavía no hay rondas registradas."

### 3.6 Desconexión

- Banner rojo fijo: "Sin conexión con el robot. Última actualización 22:41:05."
- Todos los datos con opacidad 50 % y etiqueta ANTIGUO.
- Botones de control deshabilitados ("Sin conexión") salvo Detención operativa, que advierte: "Puede quedar sin respuesta."
- Órdenes sin confirmar quedan visibles en "Sin respuesta".
- Al volver la conexión: banner verde "Conexión recuperada. Reanudá la ronda cuando estés listo."

### 3.7 Modal de confirmación

- Título "¿Cancelar la ronda?" o "¿Detención operativa?"
- Texto: "El robot volverá a la base." / "Es un comando de software simulado y no reemplaza una parada de emergencia física."
- Botones: "Volver" (secundario) · "Confirmar" (rojo).

### 3.8 Panel de simulación (hoja inferior, solo demo)

- Botones: Forzar desconexión · Reconectar · Rechazar próxima orden · Batería baja · Persona detectada · Zona no verificable.
- Texto: "Solo para la demostración."

## 4. Flujos que hay que mostrar en el Figma

- **Flujo normal:** Selección → Panel → Iniciar ronda (orden Pendiente → Confirmada) → Rondas con avance P1 a P6 → Resumen de cierre → Historial.
- **Flujo con falla (persona detectada):** Panel en ronda → alerta crítica "Persona detectada" → Alertas → Reconocer → Reanudar o Volver a la base.
- **Flujo con falla (desconexión):** Panel → pierde conexión → datos antiguos + órdenes sin respuesta → recupera → Reanudar.

## 5. Para el Figma: cómo armarlo rápido

1. Crear un frame de 360 × 800 y definir los estilos de color y texto de la sección 1.
2. Armar los componentes (sección 1) con sus 4 estados usando variantes.
3. Armar las pantallas 3.1 a 3.6 y el modal.
4. Conectar los flujos del punto 4 con Prototype (Navigate to / Open overlay).
5. Copiar el enlace con "Share → Anyone with the link can view".
