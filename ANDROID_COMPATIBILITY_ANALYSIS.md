# Analisis de compatibilidad Android - dropIn DH

Fecha: 2026-02-12
Proyecto analizado: `DHMeter`

## Estado tras correcciones aplicadas (2026-02-12)

Se aplicaron mejoras concretas de compatibilidad:
- `compileSdk` y `targetSdk` subidos a `35` en todos los modulos.
- Fallback de GPS sin GMS implementado en `GpsCollector`:
  - usa `FusedLocationProviderClient` cuando hay Play Services,
  - cae a `LocationManager` (GPS/Network provider) cuando no hay GMS o falla Fused.
- WakeLock defensivo con timeout en `RecordingService` (`acquire(timeout)`), mas logs de errores operativos.
- Se removio bloqueo de orientacion fija `portrait` en `MainActivity` (mejor soporte tablets/ChromeOS).
- Se elimino permiso `HIGH_SAMPLING_RATE_SENSORS` y se limito muestreo IMU a 200Hz para evitar requisito/politica especial.
- Se actualizaron dependencias criticas seguras sin romper el stack actual:
  - `androidx.core:core-ktx` -> `1.13.1`
  - `androidx.appcompat:appcompat` -> `1.7.1`
  - `com.google.android.gms:play-services-location` -> `21.3.0`
  - `com.google.android.gms:play-services-maps` -> `19.1.0`

Verificacion local post-cambios:
- `:app:assembleDebug` OK
- `:app:assembleRelease` OK
- `:app:lintDebug` OK (`0 errors, 128 warnings`)

## Resumen ejecutivo

No es posible afirmar 100% "funciona en todos los Android" sin una granja de dispositivos reales (OEMs, chips, capas de energia y sensores distintos).

Estado actual con evidencia local:
- Compila en debug: OK (`:app:assembleDebug`)
- Compila en release: OK (`:app:assembleRelease`)
- Tests unitarios debug: OK (`:app:testDebugUnitTest`)
- Lint debug: OK sin errores bloqueantes (`0 errors, 126 warnings`)

Conclusion tecnica:
- Soporte real esperado: Android 8.0+ (API 26+) con hardware y servicios requeridos.
- No es "todos los Android": quedan fuera versiones antiguas, equipos sin sensores requeridos y dispositivos sin Google Play Services.

## Base de compatibilidad declarada

`app/build.gradle.kts`:
- `minSdk = 26`
- `targetSdk = 35`
- `compileSdk = 35`

`app/src/main/AndroidManifest.xml`:
- Permisos clave: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `WAKE_LOCK`
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
- Riesgo de cumplimiento Play por target SDK: mitigado al subir a `targetSdk 35`.

## Compatibilidad por tipo de dispositivo

### Telefonos Android con GMS (Google Play Services)
- Mejor escenario de compatibilidad.

### Dispositivos sin GMS (ej. varios Huawei)
- Riesgo medio: ahora hay fallback nativo con `LocationManager` cuando no hay Play Services.
- Puede degradarse precision/frecuencia segun provider disponible (GPS vs network).

### Tablets
- Compatibilidad mejorada al remover bloqueo fijo en portrait.
- Aun se recomienda pruebas UI en pantallas grandes/ChromeOS para optimizar layout.

### Wear OS / Android TV / automotriz
- No objetivo funcional de esta app por sensores/interfaz.

### Equipos sin giroscopio o sin GPS real
- No instalables o no funcionales por `uses-feature` requeridas.

## Hallazgos relevantes de lint (impacto real)

1. Dependencias aun con warnings de obsolescencia
- Se actualizaron algunas criticas (`core-ktx`, `appcompat`, `play-services-location`, `play-services-maps`), pero quedan otras por migrar.
- Impacto: riesgo moderado de bugs/resoluciones tardias en OEM/versiones futuras.

2. Stack de build (AGP/Kotlin/Compose) atrasado para subir mas dependencias
- Varias versiones recientes de AndroidX exigen AGP mas nuevo.
- Impacto: limita mejoras de compatibilidad hasta ejecutar una migracion de toolchain.

3. Warnings de lint no funcionales (typos, recursos no usados, tipografia)
- No bloquean ejecucion, pero mantienen ruido tecnico.
- Impacto: bajo en runtime, medio en mantenibilidad.

## Riesgos funcionales en campo (no detectables solo con build)

- Diferencias OEM de ahorro de energia (Xiaomi, Oppo, Vivo, Samsung en ciertos modos).
- Precision GPS variable por chipset/antena.
- Frecuencia de sensores reducida en dispositivos de gama baja.
- Restricciones de inicio/continuidad del foreground service si el usuario fuerza cierre o el sistema optimiza agresivamente.

## Recomendaciones para ampliar compatibilidad real

Prioridad alta:
1. Crear matriz de pruebas reales minima por OEM/version:
   - Samsung (API 30/33/34)
   - Xiaomi (API 30/33)
   - Motorola (API 29/33)
   - Pixel (API 34/35)
2. Planificar upgrade de toolchain (AGP/Kotlin/Compose) para desbloquear dependencias mas nuevas.

Prioridad media:
1. Seguir actualizando dependencias criticas por tandas (lifecycle/navigation/work/testing) tras upgrade de AGP.
2. Mantener validaciones de sensores y mensajes claros al usuario cuando un sensor no existe o tiene baja calidad.
3. Limpiar warnings de lint no funcionales para reducir deuda tecnica.

## Dictamen final

- Estado actual: funcional para un subconjunto amplio de telefonos Android modernos con sensores requeridos y Google Play Services.
- No alcanza para afirmar "todos los Android" en sentido estricto.
- Para acercarse a esa meta se requiere device testing real multi-OEM y ajustes de robustez en background/sensores/GMS fallback.
