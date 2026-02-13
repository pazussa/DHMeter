# Analisis de compatibilidad Android - DHMeter

Fecha: 2026-02-13  
Proyecto: `DHMeter`

## Resumen ejecutivo

No es tecnicamente posible asegurar "funciona en todos los Android" sin una matriz amplia de pruebas reales en multiples OEM/versiones/chipsets.

Con la evidencia ejecutada hoy en este repositorio:

- `:app:assembleDebug` -> OK
- `:app:assembleRelease` -> OK
- `:app:testDebugUnitTest` -> OK (1 test, 0 fallos)
- `:app:lintDebug` -> OK (0 errores, 127 warnings)
- `lint vital release` -> OK (`No issues found`)

Dictamen: la app esta bien encaminada para **Android 8.0+ (API 26+)** en telefonos con sensores requeridos, pero **no cubre "todos los Android"**.

## Evidencia verificada

## 1) Base de soporte por SDK

Fuente: `app/build.gradle.kts`

- `minSdk = 26`
- `targetSdk = 35`
- `compileSdk = 35`

Implicacion:
- Android 5/6/7 no estan soportados.
- El rango objetivo real es Android 8.0+.

## 2) Restriccion por hardware requerido

Fuente: `app/src/main/AndroidManifest.xml` y manifest merge release.

Features obligatorias:
- `android.hardware.sensor.accelerometer` (required=true)
- `android.hardware.sensor.gyroscope` (required=true)
- `android.hardware.location.gps` (required=true)
- `android:glEsVersion=0x00020000` (required=true, inyectado por dependencias de mapas)

Implicacion:
- Equipos sin giroscopio o sin GPS quedan fuera en Play.
- No es una app universal para todo tipo de Android.

## 3) Permisos y servicios en runtime

Fuentes:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/dhmeter/app/ui/components/PermissionHandler.kt`
- `app/src/main/java/com/dhmeter/app/service/RecordingService.kt`

Puntos relevantes:
- Usa permisos de ubicacion (`FINE/COARSE`), foreground service location y notificaciones.
- `RecordingService` corre como foreground service (`foregroundServiceType="location"`).
- Hay `WAKE_LOCK` con timeout defensivo de 2 horas.

Implicacion:
- Compatibilidad buena en APIs modernas, pero puede haber cortes en OEM con ahorro de bateria agresivo.

## 4) Manejo de dispositivos sin Google Play Services

Fuente: `sensing/src/main/java/com/dhmeter/sensing/collector/GpsCollector.kt`

Hallazgo:
- Si Fused Location no esta disponible, cae a `LocationManager` (GPS/network provider).

Implicacion:
- Mejora compatibilidad de **captura GPS** en equipos sin GMS.
- Aun asi, funciones de mapas/billing/comunidad cloud pueden degradarse fuera del ecosistema Google.

## 5) Resultados de calidad tecnica (hoy)

### Build y tests

- APK debug generado: `app/build/outputs/apk/debug/app-debug.apk`
- APK release generado: `app/build/outputs/apk/release/app-release.apk`
- Resultado test unitario: `app/build/test-results/testDebugUnitTest/TEST-com.dhmeter.app.simulation.LiveVsChartsSimulationTest.xml`
  - tests=1, failures=0, errors=0

### Lint

Fuente: `app/build/reports/lint-results-debug.txt` y `app/build/reports/lint-results-debug.xml`

Resumen:
- 0 errores, 127 warnings
- Top warnings por tipo:
  - `UnusedResources`: 101
  - `GradleDependency`: 13
  - `Typos`: 6
  - `TypographyEllipsis`: 4
  - otros menores: 3

Impacto en compatibilidad:
- No hay errores bloqueantes de lint.
- Los warnings actuales afectan mas mantenibilidad/actualizacion que runtime inmediato.

## Riesgos reales de compatibilidad en campo

## Riesgo alto

1. Dispositivos sin giroscopio o sin GPS real
- No instalables o no funcionales por `uses-feature required=true`.

2. Android < 8.0 (API < 26)
- No soportado por diseno.

## Riesgo medio

1. OEM con politicas de bateria agresivas (Xiaomi/Oppo/Vivo/Samsung en modos restrictivos)
- Posibles cortes de sensores/GPS/servicio en segundo plano durante sesiones largas.

2. Equipos sin Play Store / GMS
- Captura base puede funcionar (fallback GPS), pero monetizacion y partes del stack Google pueden verse limitadas.

3. Dependencias no actualizadas a ultimas versiones
- No rompe hoy, pero eleva riesgo de incompatibilidad futura con nuevos dispositivos/ROM.

## Riesgo bajo

1. Warnings de typos/recursos no usados
- Poco impacto funcional directo.

## Que si se puede afirmar hoy

- La app **compila y pasa pruebas unitarias** localmente para su baseline actual.
- La app esta preparada para **Android 8.0+** con sensores requeridos.
- El flujo de captura usa medidas de robustez relevantes:
  - fallback GPS sin GMS
  - foreground service
  - control de wake lock con timeout

## Que no se puede afirmar hoy

- No se puede afirmar "funciona en todos los Android" sin:
  - pruebas instrumentadas en dispositivos fisicos multi-OEM
  - validacion de sesiones reales largas en campo
  - validacion especifica en equipos sin GMS para todas las features (no solo GPS)

## Matriz minima recomendada para cerrar compatibilidad real

Ejecutar pruebas reales al menos en:

1. Pixel (API 34/35)
2. Samsung gama media y alta (API 33/34)
3. Xiaomi/Redmi (API 30/33)
4. Motorola (API 29/33)
5. Un dispositivo sin GMS (si es target de mercado)

Casos por equipo:

1. Sesion de grabacion de 20-30 min con pantalla apagada
2. Perdida y recuperacion de GPS
3. Inicio/parada repetida de grabacion (10+ ciclos)
4. Cierre por sistema y recuperacion de app
5. Flujo Pro (billing) en equipos con Play Store

## Acciones recomendadas (prioridad)

## Alta

1. Ejecutar matriz de pruebas fisicas multi-OEM (arriba).
2. Definir politica clara para optimizacion de bateria (guia in-app por fabricante si aplica).
3. Verificar cobertura real de funciones Google en devices sin GMS segun mercado objetivo.

## Media

1. Reducir warnings de dependencias desactualizadas y planificar upgrade gradual.
2. Limpiar recursos no usados para bajar ruido tecnico.

## Baja

1. Corregir typos/typography de strings.

---

## Conclusion final

Estado actual: **compatible de forma solida con una parte amplia de Android modernos (API 26+)**, pero no universal para "todos los Android".  
La principal brecha no es de compilacion sino de **validacion en dispositivos reales** y variabilidad OEM en ejecucion continua de sensores/GPS en segundo plano.
