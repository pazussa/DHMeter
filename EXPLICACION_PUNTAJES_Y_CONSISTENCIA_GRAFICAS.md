# Explicacion de Puntajes y Consistencia con Graficas

## 1. Resumen ejecutivo

- Los puntajes de `Impacto`, `Vibracion` y `Estabilidad` se calculan en 3 etapas:
1. metrica cruda por ventana,
2. resumen crudo por bajada,
3. normalizacion a escala 0-100.
- **Si son consistentes matematicamente con las graficas en direccion/tendencia** (mas carga cruda -> peor).
- **No son equivalentes numericamente 1:1** con lo que ves en las graficas, porque usan:
1. agregaciones distintas (suma/promedio),
2. referencias distintas,
3. funciones de normalizacion distintas (lineal vs saturante).

## 2. Pipeline exacto de calculo

### 2.1 Ventaneo de la senal

En `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:36` y `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:37`:

- `WINDOW_SIZE_SEC = 1.0`
- `HOP_SIZE_SEC = 0.25`

Esto genera ventanas solapadas (75% de overlap) y por cada ventana se calcula:
- `impact`
- `harshness`
- `stability`

Se guardan en listas (ver `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:230`, `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:231`, `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt:232`).

### 2.2 Impacto crudo por ventana

En `signal/src/main/java/com/dhmeter/signal/metrics/ImpactAnalyzer.kt`:
- Umbral base: `IMPACT_THRESHOLD_MS2 = 2.5` (`.../ImpactAnalyzer.kt:21`)
- Distancia minima entre picos: `100 ms` (`.../ImpactAnalyzer.kt:22`)
- Umbral ajustado por sensibilidad:

`threshold = 2.5 / impactSensitivity` (`.../ImpactAnalyzer.kt:77-79`)

Para cada pico detectado:

`peakG = magnitud / 9.81`

`impact_window = sum(peakG^2)` (`.../ImpactAnalyzer.kt:46`)

### 2.3 Vibracion cruda por ventana

En `signal/src/main/java/com/dhmeter/signal/metrics/HarshnessAnalyzer.kt`:

- Magnitud de aceleracion.
- Filtro pasa-altas aproximado por diferencia:

`highFreq_i = (mag[i+1] - mag[i])^2` (`.../HarshnessAnalyzer.kt:29`)

- RMS:

`baseRms = sqrt(promedio(highFreq))` (`.../HarshnessAnalyzer.kt:31`)

- Ajuste por sensibilidad:

`harshness_window = baseRms * harshnessSensitivity` (`.../HarshnessAnalyzer.kt:36`)

### 2.4 Estabilidad cruda por ventana

En `signal/src/main/java/com/dhmeter/signal/metrics/StabilityAnalyzer.kt`:

- Varianzas de giroscopio en X e Y (`.../StabilityAnalyzer.kt:37`, `.../StabilityAnalyzer.kt:38`)
- Indice:

`stability_window = (varX + varY) * stabilitySensitivity` (`.../StabilityAnalyzer.kt:44`)

Nota del propio codigo: menor valor = mas estable (`.../StabilityAnalyzer.kt:27`).

## 3. Como se obtiene el valor crudo de cada metrica en la bajada

En `signal/src/main/java/com/dhmeter/signal/processor/SignalProcessor.kt`:

- `impactScore = suma de todas las ventanas` (`.../SignalProcessor.kt:67`)
- `harshnessAvg = promedio de ventanas` (`.../SignalProcessor.kt:68`)
- `stabilityScore = promedio de ventanas` (`.../SignalProcessor.kt:72`)

Formalmente:

- `Impacto_crudo_run = sum(impact_window_i)`
- `Vibracion_cruda_run = promedio(harshness_window_i)`
- `Estabilidad_cruda_run = promedio(stability_window_i)`

## 4. Conversion de crudo a puntaje mostrado en cards (0-100, mas alto = mejor)

En `app/src/main/java/com/dhmeter/app/ui/metrics/RunScoreUtils.kt`:

- Referencias de run:
  - `IMPACT_REF = 25` (`.../RunScoreUtils.kt:7`)
  - `HARSHNESS_REF = 1.2` (`.../RunScoreUtils.kt:8`)
  - `STABILITY_REF = 0.35` (`.../RunScoreUtils.kt:9`)

### 4.1 Burden de run (0-100, menor = mejor)

`burden_run = 100 * x / (x + REF)` (`.../RunScoreUtils.kt:22-24`)

donde `x` es el crudo de run (sum o promedio segun metrica).

### 4.2 Quality de run (0-100, mayor = mejor)

`quality_run = 100 - burden_run` (`.../RunScoreUtils.kt:48`)

Equivalente:

`quality_run = 100 * REF / (x + REF)`

Se usa en:
- `runMetricQualityScore(...)` (`.../RunScoreUtils.kt:52`)
- `runOverallQualityScore(...)` promedio de las 3 calidades (`.../RunScoreUtils.kt:57-64`)

## 5. Como se calculan los valores en las graficas (0-100 burden)

Las graficas de impacto/vibracion/estabilidad usan `normalizeSeriesBurdenScore(...)` en:
- `app/src/main/java/com/dhmeter/app/ui/screens/runsummary/RunSummaryScreen.kt:912`
- `app/src/main/java/com/dhmeter/app/ui/screens/charts/ChartsScreen.kt:395`

Normalizacion de serie en `app/src/main/java/com/dhmeter/app/ui/metrics/RunScoreUtils.kt:34-40`:

- Referencias de serie:
  - `IMPACT_SERIES_REF = 5` (`.../RunScoreUtils.kt:11`)
  - `HARSHNESS_SERIES_REF = 3` (`.../RunScoreUtils.kt:12`)
  - `STABILITY_SERIES_REF = 0.5` (`.../RunScoreUtils.kt:13`)

- Formula por punto:

`burden_chart_point = clamp(100 * y / SERIES_REF, 0, 100)`

donde `y` es el valor crudo del punto de serie.

## 6. Consistencia matematica con las graficas: evaluacion

## 6.1 Donde SI hay consistencia

- Mismo origen de senal: las cards y las graficas salen de las mismas metricas base por ventana.
- Misma direccion fisica:
  - mas impacto/vibracion/inestabilidad cruda -> peor.
- Escala de graficas declarada como burden (menor mejor), coherente con texto en UI:
  - `runsummary`: `.../RunSummaryScreen.kt:669`
  - `charts`: `.../ChartsScreen.kt:233`

## 6.2 Donde NO hay equivalencia numerica exacta (punto clave)

1. **Agregacion distinta**:
   - Impacto run usa **suma** de ventanas.
   - Vibracion/Estabilidad run usan **promedio**.
   - Graficas muestran **puntos por distancia** (serie), no el resumen final.

2. **Normalizacion distinta**:
   - Cards: `x/(x+REF)` (saturante).
   - Graficas: `x/SERIES_REF` (lineal con clamp).

3. **Referencias distintas**:
   - Run refs: `25`, `1.2`, `0.35`.
   - Serie refs: `5`, `3`, `0.5`.

4. **Impacto particularmente no comparable 1:1**:
   - El valor de run depende de la suma total, por lo que duracion/cantidad de ventanas afecta.
   - La grafica muestra intensidad local por distancia.

Conclusion tecnica:
- **Consistencia monotona: SI** (si sube la carga cruda, empeora todo).
- **Consistencia numerica directa card vs grafica: NO** (no existe transformacion unica simple que haga coincidir ambos valores).

## 7. Coherencia con pantalla de comparacion

`CompareMultipleRunsUseCase` reutiliza la misma normalizacion de burden de run (`x/(x+REF)`) para impacto/vibracion/estabilidad:
- refs en `domain/src/main/java/com/dhmeter/domain/usecase/CompareMultipleRunsUseCase.kt:16-18`
- formula en `.../CompareMultipleRunsUseCase.kt:141-143`

Por eso en comparacion es correcto el criterio `lowerIsBetter = true` (`.../CompareMultipleRunsUseCase.kt:102-115`).

## 8. Validacion automatizada existente

Hay un test que valida alineacion entre monitor en vivo y normalizacion de serie para impacto y vibracion:
- `app/src/test/java/com/dhmeter/app/simulation/LiveVsChartsSimulationTest.kt:29`
- compara `live` vs `normalizeSeriesBurdenScore(...)/100` (`.../LiveVsChartsSimulationTest.kt:52-58`)

Esto respalda coherencia de la escala de grafica con la parte live, pero no implica igualdad numerica con el score final de cards (porque ese score usa otra formula y agregacion).

## 9. Coherencia con objetivo de vida real (downhill)

Objetivo real implicito del producto:
- premiar bajadas mas controladas y menos castigadoras para rider/bici,
- penalizar impactos duros, vibracion alta e inestabilidad.

Veredicto global:
- **SI es coherente a nivel de direccion fisica y utilidad practica**.
- **NO es aun un score absoluto "de verdad universal"**.
- En estado actual: **coherencia real aproximada 7/10**.

### 9.1 Impacto (coherencia real: media)

Lo bueno:
- usa picos reales sobre umbral y pondera por energia (`peakG^2`), lo que si representa golpes mas fuertes.

Lo mejorable para vida real:
- el score final de impacto se calcula como **suma** de ventanas (`SignalProcessor.kt:67`), con ventanas solapadas (1.0 s / hop 0.25 s), por lo que un mismo evento puede influir en varias ventanas.
- eso introduce sesgo por duracion/longitud de bajada (a igual estilo, una bajada mas larga puede salir peor solo por acumular mas).

Impacto en interpretacion real:
- bueno como "carga acumulada total",
- menos bueno como comparacion justa entre bajadas de duracion distinta si no se normaliza por tiempo o distancia.

### 9.2 Vibracion (coherencia real: media-alta)

Lo bueno:
- el RMS de diferencias captura bien terreno "chatter"/aspereza de forma robusta y simple.

Lo mejorable:
- el comentario de dominio menciona banda 15-40Hz, pero en implementacion se usa aproximacion por diferencia (no un band-pass real).
- puede mezclar ruido de sensor/montaje con vibracion real, dependiendo de telefono y fijacion.

Impacto en vida real:
- util para comparar tendencia de suavidad,
- menos fiable como magnitud fisica exacta entre dispositivos distintos.

### 9.3 Estabilidad (coherencia real: media)

Lo bueno:
- varianza de gyro X+Y representa bien descontrol angular general.

Lo mejorable:
- no distingue entre inestabilidad "mala" y maniobra agresiva/intencional (curvas rapidas, saltos, correcciones deportivas).
- depende bastante de posicion del telefono y setup del rider.

Impacto en vida real:
- bueno para detectar bajadas erraticas,
- puede penalizar estilo agresivo pero tecnico en pistas exigentes.

### 9.4 Normalizacion y calibracion (coherencia real: media)

Lo bueno:
- la transformacion saturante `x/(x+REF)` evita extremos y hace scores estables.

Lo mejorable:
- refs fijos (`25`, `1.2`, `0.35`) no estan adaptados por rider, bici, montaje, ni tipo de pista.
- eso limita comparabilidad absoluta entre usuarios/tracks.

Impacto en vida real:
- bueno para seguimiento personal en condiciones parecidas,
- menos bueno para ranking absoluto entre contextos distintos.

## 10. Conclusiones practicas

Hoy el sistema sirve bien para:
- comparar **tu propia evolucion** en el mismo track y setup,
- detectar si una bajada fue globalmente mas suave o mas castigadora.

Hoy el sistema no debe venderse como:
- medicion absoluta universal entre riders/dispositivos/tracks muy distintos.

Si se quiere subir coherencia real a nivel "competitivo" (8.5-9/10):
1. normalizar impacto por distancia o tiempo (no solo suma),
2. usar filtro de vibracion band-pass real (por ejemplo 15-40Hz),
3. calibrar refs por dispositivo/montaje y por tipo de pista,
4. agregar un indice de confianza del score segun calidad de senal/GPS.


