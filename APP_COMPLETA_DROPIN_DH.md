# APP COMPLETA - dropIn DH

Este documento resume el estado actual completo de la app en este repositorio (`DHMeter`), con foco tecnico y funcional, de forma concreta.

## 1. Producto

- Nombre visible: `dropIn DH`
- Package Android: `com.dhmeter.app`
- Objetivo: telemetria downhill (MTB) con celular, para grabar bajadas, analizar impactos/vibracion/inestabilidad/velocidad, comparar runs y revisar mapa/eventos.
- Lema en Home: `Rider, saca el maximo de tus bajadas.`
- Idioma por defecto: espanol (`AppLanguageManager`, default `es`).

## 2. Stack y plataforma

- Lenguaje: Kotlin
- UI: Jetpack Compose + Material3
- DI: Hilt
- Persistencia local: Room
- Sensores: SensorManager (LINEAR_ACCELERATION, GYROSCOPE, ROTATION_VECTOR), GPS (Fused + fallback LocationManager)
- Mapas: Google Maps Compose
- Comunidad en tiempo real: Firebase Auth anonima + Firestore
- Monetizacion: Google Play Billing (suscripciones)

Configuracion Android:

- `minSdk = 26`
- `targetSdk = 35`
- `compileSdk = 35`
- Java/Kotlin target: 17

## 3. Estructura modular

| Modulo | Rol principal |
|---|---|
| `:app` | UI, navegacion, servicio foreground, comunidad, monetizacion, localizacion |
| `:domain` | Modelos de negocio, repositorios (interfaces), casos de uso |
| `:data` | Room, DAO, entidades, mappers, implementaciones de repositorios |
| `:sensing` | Captura de sensores/GPS, buffers, live monitor, preview |
| `:signal` | Procesamiento de senal y generacion de metricas/eventos/series |
| `:charts` | Componentes de graficas reutilizables |
| `:core` | Utilidades base (distancia, tiempo, metricas) |

## 4. Navegacion y pantallas

Rutas definidas en `Navigation.kt`:

- `home`
- `recording/{trackId}`
- `run_summary/{runId}`
- `compare/{trackId}/{runIds}`
- `charts/{trackId}/{runIds}`
- `events/{runId}`
- `run_map/{runId}`
- `history`
- `track_detail/{trackId}`
- `community`
- `pro`

Resumen funcional por pantalla:

- Home:
  - Lista tracks
  - Crear track
  - Estado sensores
  - Cambio EN/ES
  - Dialogo de ayuda/contacto (`dropindh@gmail.com`)
  - Boton Comunidad en barra inferior
- Recording:
  - Preview live (impacto/vibracion/inestabilidad)
  - Sensibilidad de sensores (panel amplio, rango 0.1..5.0)
  - Inicio manual con cuenta regresiva de 10s
  - Auto-start por segmentos locales
  - Stop y procesamiento
- Run Summary:
  - Metricas del run
  - Graficas: impacto, vibracion, inestabilidad, velocidad
  - Heatmap de velocidad
  - Max speed en puntajes
  - Enlace a mapa y eventos
- Compare:
  - Comparacion multi-run
  - Tabla de metricas y veredicto
  - Mapa superpuesto de rutas
  - Secciones S1..Sn seleccionables en mapa
  - Tabla por seccion con tiempos y velocidad promedio por seccion
- Charts:
  - Graficas multi-run para impacto/vibracion/estabilidad/velocidad
  - Eventos sobre distancia
  - Heatmaps de velocidad por run
- Map:
  - Mapa de un run con polilinea y segmentos por severidad
  - Mapa base hibrido por defecto
  - Eventos con detalle
  - Comparacion por secciones contra la bajada mas rapida (al tocar ruta)
- Events:
  - Lista de eventos detectados y resumen por tipo
- History:
  - Historial de tracks
- Track Detail:
  - Lista de runs del track
  - Seleccion multi-run para comparar
- Community:
  - Registro con username unico y ciudad/lugar
  - Chat grupal real entre dispositivos
  - Lista riders + progreso basico
  - Reportar/bloquear usuarios y reportar mensajes
  - Eliminar cuenta de comunidad
- Pro:
  - Suscripciones mensuales/anuales (Billing)
  - Restaurar compras

## 5. Flujo completo de una bajada

1. Usuario crea/elige track en Home.
2. En Recording:
   - Inicio manual: 10s de countdown antes de arrancar.
   - O auto-start: armado por segmentos locales (inicio y direccion).
3. `RecordingService` mantiene foreground service + wake lock para operar incluso con pantalla apagada.
4. `RecordingManager` captura IMU + GPS y guarda en `SensorBuffers`.
5. Al detener:
   - `SignalProcessor` procesa ventanas y eventos.
   - Se genera `Run`, `RunSeries`, `RunEvent`, `GpsPolyline`.
   - Se guarda en Room.
6. Se abre Run Summary con metricas, charts, mapa y comparaciones.

## 6. Sensores, rangos y sensibilidad

Sensores requeridos por manifiesto:

- GPS
- acelerometro
- giroscopio

Sensores usados en runtime:

- `TYPE_LINEAR_ACCELERATION` (impacto/vibracion)
- `TYPE_GYROSCOPE` (inestabilidad)
- `TYPE_ROTATION_VECTOR` (opcional)
- GPS (Fused + fallback a `LocationManager`)

Sensibilidad configurable (persistida en `SharedPreferences`):

- Rango: `0.1` a `5.0`
- Defaults:
  - `impact = 0.62`
  - `vibration/harshness = 5.0`
  - `inestability/stability = 0.1`
  - `gps = 1.0`

Impacto en sistema:

- Ajusta thresholds de impacto.
- Escala harshness e instability.
- Ajusta exigencia de precision GPS y validaciones.
- Afecta preview en vivo y grabaciones nuevas.

## 7. Procesamiento de senal y metricas

Pipeline principal (`SignalProcessor`):

- Ventanas: `1.0s` con hop `0.25s`
- Salida normalizada a series de `200` puntos (0..100% de distancia)
- Series:
  - `IMPACT_DENSITY`
  - `HARSHNESS`
  - `STABILITY` (semantica de inestabilidad: mayor valor = peor)
  - `SPEED_TIME` (perfil tiempo vs distancia para split timing)

Eventos detectados:

- `LANDING` (deteccion por pico tras airtime)
- `IMPACT_PEAK`
- `HARSHNESS_BURST` (soporte en modelos/UI)

Validaciones de run (`RunValidator`):

- duracion minima/maxima
- distancia minima
- calidad GPS
- continuidad de movimiento
- calidad de senal IMU

Notas de scoring:

- Charts de carga usan escala 0..100 (burden).
- UI muestra puntajes con 2 decimales (`formatScore0to100`).
- Velocidad maxima se incluye en run summary y comparaciones.

## 8. Grabacion en segundo plano, bloqueo de pantalla y auto-start

`RecordingService` hace:

- Foreground notification permanente durante armado/recording.
- Wake lock parcial para continuidad con pantalla apagada.
- Preview de ubicacion para detectar inicio de segmento.
- Auto-stop cerca de fin de segmento.

Condiciones de auto-start (resumen):

- estar cerca del inicio de segmento
- velocidad minima
- precision GPS minima
- direccion alineada con el segmento
- cooldown anti-retrigger

## 9. Persistencia local (Room)

Base: `dhmeter.db` (`version = 4`)

Tablas:

- `tracks`
- `runs`
- `run_series`
- `events`
- `gps_polylines`

Se guarda:

- resumen de run (duracion, distancia, gpsQuality, scores, avg/max speed, etc.)
- series por tipo (binario packed float)
- eventos por run
- polilinea simplificada GPS para mapa

Migraciones:

- 1->2: agrega `gps_polylines`
- 2->3: agrega `distanceMeters`, `pauseCount`
- 3->4: agrega `maxSpeed`

Importante: tambien existe `fallbackToDestructiveMigration()` en el builder.

## 10. Mapas y comparacion espacial/temporal

Mapa individual (`MapScreen`):

- Tipo de mapa por defecto: `HYBRID`
- Polilinea base + segmentos coloreados por severidad
- Heatmap orientado a velocidad por defecto
- Eventos opcionales
- Al tocar la ruta: abre comparacion de secciones vs run mas rapido del track

Mapa en comparacion multi-run (`CompareScreen`):

- Overlay de rutas de varios runs
- Marcadores S1..Sn
- Al tocar un marcador de seccion, se resalta esa seccion en la tabla
- Tabla muestra tiempo por seccion y velocidad promedio por seccion para cada run

## 11. Comunidad (real, no simulada)

Backend real:

- Firebase Auth anonima
- Firestore

Colecciones:

- `community_users`
- `community_usernames`
- `community_messages`
- `community_reports`
- subcoleccion `blocked_users`

Capacidades:

- username unico (transaccion en `community_usernames`)
- chat grupal en tiempo real
- progreso basico rider (runs totales, mejor tiempo, avg/max speed)
- moderacion: reportar/bloquear
- borrado de cuenta de comunidad y limpieza de sus datos principales

Configuracion requerida en `gradle.properties`:

- `FIREBASE_API_KEY`
- `FIREBASE_APP_ID`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_STORAGE_BUCKET`
- `FIREBASE_MESSAGING_SENDER_ID`

## 12. Monetizacion

Implementacion actual:

- Play Billing suscripciones:
  - `pro_monthly`
  - `pro_yearly`
- Pantalla Pro para compra/restauracion/sync.
- Tracking local de eventos de monetizacion en `SharedPreferences` + log.

Limite actual:

- validacion de compra es local (`LocalPurchaseValidator`), sin backend de validacion server-side.

## 13. Idioma, contenido y UX base

- Soporte EN/ES con selector en Home.
- Espanol por defecto al primer arranque.
- Strings en recursos (`values`, `values-es`) y helper `tr()` en Compose.
- Estilo visual actual:
  - tema oscuro tipo racing DH
  - fondo con gradientes + lineas diagonales
  - componentes glass/translucidos

## 14. Permisos y manifiesto

Permisos declarados:

- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`
- `VIBRATE`
- `WAKE_LOCK`

Features:

- accelerometer requerido
- gyroscope requerido
- gps requerido
- barometer opcional

Servicio:

- `RecordingService` como foreground service de tipo location.

## 15. Build, firma y APK

Comandos utiles:

- Debug: `./gradlew :app:assembleDebug`
- Release: `./gradlew :app:assembleRelease`
- Tests unitarios: `./gradlew :app:testDebugUnitTest`
- Lint: `./gradlew :app:lintDebug`

Salida release:

- `app/build/outputs/apk/release/app-release.apk`

Firma release actual:

- usa `devRelease` con debug keystore (`~/.android/debug.keystore`) para builds internos.

## 16. Pruebas existentes

Tests encontrados:

- `app/src/test/java/com/dhmeter/app/simulation/LiveVsChartsSimulationTest.kt`

Cobertura de ese test:

- simulacion de buffers de sensores/GPS
- verifica alineacion entre live monitor y normalizacion de charts para impacto y harshness

## 17. Documentos de apoyo existentes en raiz

- `ANDROID_COMPATIBILITY_ANALYSIS.md`
- `MONETIZACION_DROPIN_DH.md`
- `MONETIZACION_IMPLEMENTACION_ESTADO.md`
- `VIABILIDAD_PAGO_UNICO_DROPIN_DH.md`
- `MIGRACION_PAGO_UNICO_A_SUSCRIPCION.md`
- `COMMUNITY_FIREBASE_SETUP.md`

## 18. Riesgos y puntos tecnicos a cerrar antes de publicacion masiva

- `MAPS_API_KEY` esta hardcodeada en `app/build.gradle.kts` (debe salir a secreto seguro por entorno/CI).
- Firma release no productiva (debug keystore).
- Validacion de compras aun sin backend.
- `fallbackToDestructiveMigration()` puede borrar datos ante cambios de esquema no migrados.
- En UI hay mezcla de strings en resources y strings directos por `tr()`, lo que complica i18n total con escalabilidad.
- Nombre de paquete sigue en namespace historico `com.dhmeter.app` (branding actual es `dropIn DH`).

## 19. Estado funcional general (resumen ejecutivo)

- App operativa de punta a punta para:
  - crear tracks
  - grabar runs (manual y auto)
  - procesar y guardar telemetria
  - visualizar charts/mapa/eventos/summary
  - comparar runs con secciones
  - comunidad en tiempo real con backend real
  - capa inicial de monetizacion por suscripcion

- Base lista para evolucion comercial, con tareas pendientes clave en seguridad, billing server-side y endurecimiento de release.
