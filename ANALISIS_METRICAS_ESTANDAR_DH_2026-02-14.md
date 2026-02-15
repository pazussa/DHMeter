# Analisis de metricas estandar DH (estado 2026-02-14)

## 1) Alcance
Este analisis responde:
1. Cuales son las metricas mas estandar hoy en Downhill (DH).
2. Si ya estan en la app.
3. Si se pueden incluir las faltantes y con que complejidad.

Para evitar ambiguedad, separo "estandar" en 3 capas:
- **Competencia oficial (timing/ranking)**: lo que define resultado deportivo.
- **Telemetria de transmision/wearables**: lo que se esta volviendo comun en cobertura elite.
- **Telemetria de entrenamiento MTB**: metricas de uso extendido en ciclocomputadores/plataformas.

## 2) Hallazgo principal
- En **competencia oficial**, el nucleo estandar sigue siendo **tiempo + splits + gap + posicion**.
- Tu app ya cubre gran parte del nucleo competitivo local: **tiempo total, comparacion por secciones (splits), delta vs run mas rapido, velocidad y distancia**.
- Tu app tambien cubre muy bien una capa tecnica de bajada (**impacto, vibracion/harshness, inestabilidad, eventos**), que no es "reglamentaria UCI", pero si es coherente con tendencias de telemetria MTB.
- Lo principal que falta para igualar el estandar "moderno de broadcast/performance" son metricas externas: **HR/HRV/Strain/Recovery**, **power/cadence**, **jump airtime/count** explicitos y **elevacion/pendiente** robusta.

## 3) Metricas estandar actuales y cobertura en DHMeter

| Metrica | Estandar actual en DH | Estado en app | Evidencia en app | Brecha |
|---|---|---|---|---|
| Tiempo total de bajada | Core oficial | **SI** | `domain/src/main/java/com/dhmeter/domain/model/Run.kt:8` | Ninguna |
| Split timing por secciones | Core oficial (muy usado en resultados) | **SI** | `domain/src/main/java/com/dhmeter/domain/usecase/MapUseCases.kt:220` y `domain/src/main/java/com/dhmeter/domain/usecase/MapUseCases.kt:246` | Ninguna |
| Gap vs referencia | Core oficial | **SI** (vs run mas rapido local) | `domain/src/main/java/com/dhmeter/domain/usecase/MapUseCases.kt:273` | Falta gap vs leaderboard global |
| Posicion/ranking | Core oficial | **PARCIAL** | Hay comparacion contra run mas rapido: `domain/src/main/java/com/dhmeter/domain/usecase/MapUseCases.kt:235` | No ranking completo 1..N en UI/resultados |
| Velocidad (avg/max y perfil) | Muy comun en DH/MTB | **SI** | `domain/src/main/java/com/dhmeter/domain/model/Run.kt:26` y `domain/src/main/java/com/dhmeter/domain/model/Run.kt:27`; serie `SPEED_TIME`: `domain/src/main/java/com/dhmeter/domain/model/RunSeries.kt:71` | Ninguna |
| Distancia de bajada | Muy comun en DH/MTB | **SI** | `domain/src/main/java/com/dhmeter/domain/model/Run.kt:17` | Ninguna |
| Impacto (carga/picos) | Estandar tecnico de entrenamiento/seguridad (no reglamentario UCI) | **SI** | `domain/src/main/java/com/dhmeter/domain/model/Run.kt:21`; evento `IMPACT_PEAK`: `domain/src/main/java/com/dhmeter/domain/model/RunEvent.kt:29` | Ninguna |
| Vibracion / roughness | Estandar tecnico de setup MTB (de-facto) | **SI** | `domain/src/main/java/com/dhmeter/domain/model/Run.kt:22`; serie `HARSHNESS`: `domain/src/main/java/com/dhmeter/domain/model/RunSeries.kt:69`; evento `HARSHNESS_BURST`: `domain/src/main/java/com/dhmeter/domain/model/RunEvent.kt:30` | Ninguna |
| Estabilidad/inestabilidad | Estandar tecnico de control (de-facto) | **SI** | `domain/src/main/java/com/dhmeter/domain/model/Run.kt:24`; serie `STABILITY`: `domain/src/main/java/com/dhmeter/domain/model/RunSeries.kt:70` | Ninguna |
| Eventos de aterrizaje | Muy usado en analisis tecnico | **SI** | `domain/src/main/java/com/dhmeter/domain/model/RunEvent.kt:28`; deteccion: `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:259` | Falta "jump event" explicito (airtime) |
| Jump count / airtime / hang time | Comun en plataformas MTB (Garmin, etc.) | **NO/PARCIAL** | Hay landings, pero no evento salto/airtime dedicado | Agregar detector de salto + metrica airtime |
| Elevacion/pendiente de bajada | Comun en ciclismo/MTB, util para contexto | **PARCIAL** | Campo existe pero se deja null: `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:103` y `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:113` | Falta pipeline de pendiente/elevacion robusta |
| HR/HRV/Strain/Recovery/Sleep | Tendencia fuerte en DH elite broadcast | **NO** | No hay modelo/flujo de estos datos en dominio actual | Requiere integracion wearable/API externa |
| Power/Cadence | Estandar en entrenamiento ciclista | **NO** | Sin entidades/series para potencia/cadencia | Requiere sensores BLE + modelo nuevo |

## 4) Eventos que ya maneja la app (confirmado)
Eventos detectados y persistidos:
1. `LANDING`
2. `IMPACT_PEAK`
3. `HARSHNESS_BURST`

Referencias:
- Tipos de evento: `domain/src/main/java/com/dhmeter/domain/model/RunEvent.kt:27`
- Deteccion de eventos: `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:249`
- Mapeo a mapa/UI: `domain/src/main/java/com/dhmeter/domain/model/MapModels.kt:78` y `domain/src/main/java/com/dhmeter/domain/usecase/MapUseCases.kt:194`

## 5) Se pueden incluir las faltantes?

### 5.1 Factible solo con smartphone (sin hardware extra)
1. **Ranking local 1..N por pista** (ya tienes base para run mas rapido).
2. **Jump metrics** (airtime, jump count, hang time) usando IMU + GPS.
3. **Indice "Flow-like"** derivado de harshness + estabilidad + variabilidad de velocidad.
4. **Pendiente por GPS suavizada** (menor precision que barometro, pero util).

### 5.2 Requiere integracion externa
1. **Biometria** (HR/HRV/Strain/Recovery): wearable APIs.
2. **Power/Cadence**: sensores BLE y nuevo modelo de datos.
3. **Pendiente de alta calidad**: barometro dedicado o fusion GPS+DEM.

## 6) Priorizacion recomendada (impacto vs costo)
1. **Alta prioridad / bajo-medio costo**: ranking local completo + jump metrics.
2. **Media prioridad / medio costo**: pendiente/elevacion estimada y clasificacion por sectores tecnicos.
3. **Alta diferenciacion / alto costo**: integracion wearable (HR/strain/recovery) y sensores BLE (power/cadence).

## 7) Fuentes externas consultadas (estado a 2026-02-14)
1. UCI - MTB format (DH contra reloj, gana el menor tiempo):  
   https://www.uci.org/sport/mountain-bike/about-mountain-bike
2. UCI x WHOOP (2025) - integracion de datos de rendimiento/biometricos en tiempo real para broadcast:  
   https://www.uci.org/pressrelease/whoop-uci-mountain-bike-world-series-title-partner-and-official-wearable/27x6sM9M3I2c5J0dzdj5uU
3. UCI MTB World Series - estructura de resultados por evento (splits/time/gap/position en resultados):  
   https://ucimtbworldseries.com/results
4. WHOOP - definicion de metricas (Strain/Recovery/Sleep):  
   https://www.whoop.com/us/en/thelocker/whoop-101/
5. Garmin Edge - MTB Dynamics (Flow/Grit/Jump):  
   https://www8.garmin.com/manuals/webhelp/edge530/EN-US/GUID-34A6D091-D567-4AB3-8E07-9C69EB9A8E2C.html
6. Garmin fenix 8 - MTB performance recording (runs, 5 Hz para descensos):  
   https://www8.garmin.com/manuals/webhelp/fenix8/en-US/GUID-EA0AFD54-FC9A-4F0C-B98C-01BE8A4F9ECE.html
7. PubMed - indicadores de performance en DH (demanda fisiologica/tecnica):  
   https://pubmed.ncbi.nlm.nih.gov/23025296/
8. PubMed - factores determinantes de performance en DH:  
   https://pubmed.ncbi.nlm.nih.gov/25010645/
9. PubMed - aceleraciones de impacto en DH competitivo:  
   https://pubmed.ncbi.nlm.nih.gov/29606559/

## 8) Nota de interpretacion
- **Inferencia explicita**: UCI no define una "lista unica reglamentaria" para metricas de telemetria tecnica (impacto/vibracion/inestabilidad).  
  Por eso se separa entre:
  1. metricas oficiales de resultado deportivo (tiempo/splits/gap/posicion), y
  2. metricas tecnicas de analisis de bajada (de-facto en entrenamiento/broadcast/tecnologia).
