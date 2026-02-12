# Analisis de compatibilidad Android - dropIn DH

Fecha: 2026-02-12
Proyecto analizado: `DHMeter`

## Resumen ejecutivo

No es posible afirmar 100% "funciona en todos los Android" sin una granja de dispositivos reales (OEMs, chips, capas de energia y sensores distintos).

Estado actual con evidencia local:
- Compila en debug: OK (`:app:assembleDebug`)
- Compila en release: OK (`:app:assembleRelease`)
- Tests unitarios debug: OK (`:app:testDebugUnitTest`)
- Lint debug: OK sin errores bloqueantes (`0 errors, 133 warnings`)

Conclusion tecnica:
- Soporte real esperado: Android 8.0+ (API 26+) con hardware y servicios requeridos.
- No es "todos los Android": quedan fuera versiones antiguas, equipos sin sensores requeridos y dispositivos sin Google Play Services.

## Base de compatibilidad declarada

`app/build.gradle.kts`:
- `minSdk = 26`
- `targetSdk = 34`
- `compileSdk = 34`

`app/src/main/AndroidManifest.xml`:
- Permisos clave: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `WAKE_LOCK`, `HIGH_SAMPLING_RATE_SENSORS`
- Features requeridas: `accelerometer=true`, `gyroscope=true`, `gps=true`
- Servicio foreground con tipo `location`

## Compatibilidad por version Android

### Android 5/6/7 (API <= 25)
- No soportado por diseno (`minSdk = 26`).

### Android 8/9 (API 26-28)
- Compatible en principio.
- Riesgo medio en dispositivos con politicas agresivas de bateria que pueden frenar servicio/sensores en segundo plano.

### Android 10-13 (API 29-33)
- Compatible en principio.
- Debe mantenerse foreground service activo para telemetria en segundo plano.

### Android 14+ (API 34+)
- Compatible en ejecucion (segun build actual).
- Hay mas restricciones de ejecucion en background segun OEM y estado de la app.
- Para publicacion Play en 2026, `targetSdk` 34 ya es riesgo de cumplimiento (deberia subirse).

## Compatibilidad por tipo de dispositivo

### Telefonos Android con GMS (Google Play Services)
- Mejor escenario de compatibilidad.

### Dispositivos sin GMS (ej. varios Huawei)
- Riesgo alto: se usa `FusedLocationProviderClient` (Google Play Services) sin fallback nativo.
- Puede fallar geolocalizacion o degradarse funcionalidad.

### Tablets
- Puede funcionar, pero hay warning de orientacion bloqueada en portrait (`LockedOrientationActivity`).
- UX puede ser suboptima en pantallas grandes/ChromeOS.

### Wear OS / Android TV / automotriz
- No objetivo funcional de esta app por sensores/interfaz.

### Equipos sin giroscopio o sin GPS real
- No instalables o no funcionales por `uses-feature` requeridas.

## Hallazgos relevantes de lint (impacto real)

1. `HIGH_SAMPLING_RATE_SENSORS` (warning especifico de politica)
- Archivo: `app/src/main/AndroidManifest.xml` linea 23
- Impacto: puede requerir justificacion fuerte en review de Play.

2. `Wakelock` sin timeout
- Archivo: `app/src/main/java/com/dhmeter/app/service/RecordingService.kt` (lint marca linea ~652)
- Impacto: riesgo de consumo excesivo en algunos equipos y politicas OEM.

3. Orientacion bloqueada (`portrait`)
- Archivo: `app/src/main/AndroidManifest.xml` linea ~52
- Impacto: UX y compatibilidad en tablets/ChromeOS.

4. Dependencias viejas
- Hay multiples warnings de versions obsoletas (AndroidX, Maps, WorkManager, etc.).
- Impacto: mas probabilidad de bugs por OEM/version.

## Riesgos funcionales en campo (no detectables solo con build)

- Diferencias OEM de ahorro de energia (Xiaomi, Oppo, Vivo, Samsung en ciertos modos).
- Precision GPS variable por chipset/antena.
- Frecuencia de sensores reducida en dispositivos de gama baja.
- Restricciones de inicio/continuidad del foreground service si el usuario fuerza cierre o el sistema optimiza agresivamente.

## Recomendaciones para ampliar compatibilidad real

Prioridad alta:
1. Subir `targetSdk` y `compileSdk` a nivel vigente para Play.
2. Implementar fallback para equipos sin GMS (si quieres cubrir ese mercado).
3. Agregar timeout defensivo al wakelock y telemetria de errores de servicio.
4. Crear matriz de pruebas reales minima por OEM/version:
   - Samsung (API 30/33/34)
   - Xiaomi (API 30/33)
   - Motorola (API 29/33)
   - Pixel (API 34/35)

Prioridad media:
1. Actualizar dependencias criticas (location/maps/lifecycle/navigation/work).
2. Evaluar remover bloqueo portrait en tablets.
3. Mantener validaciones de sensores y mensajes claros al usuario cuando un sensor no existe o tiene baja calidad.

## Dictamen final

- Estado actual: funcional para un subconjunto amplio de telefonos Android modernos con sensores requeridos y Google Play Services.
- No alcanza para afirmar "todos los Android" en sentido estricto.
- Para acercarse a esa meta se requiere device testing real multi-OEM y ajustes de robustez en background/sensores/GMS fallback.
