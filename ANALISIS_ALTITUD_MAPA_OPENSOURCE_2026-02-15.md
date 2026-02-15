# ANALISIS: Altitud para mapa de descenso (opcion sin pago / open source)

Fecha: 2026-02-15
Proyecto: DHMeter

## 1) Concluson corta

Si, se puede incluir altitud para ver el perfil real del descenso.
En este proyecto ya capturas altitud GPS en tiempo real, pero hoy no llega al modelo que pinta el mapa.

La opcion mas solida sin pago por request y open source para produccion es:
1. OpenTopoData self-hosted (MIT) con dataset SRTM/NED.
2. Usar la altitud del GPS del telefono como fallback offline.

## 2) Estado actual en tu codigo (hallazgos)

Ya existe captura de altitud:
- `sensing/src/main/java/com/dhmeter/sensing/data/SensorSamples.kt`: `GpsSample.altitude`.
- `sensing/src/main/java/com/dhmeter/sensing/collector/GpsCollector.kt`: toma `location.altitude`.

Donde se pierde:
- `domain/src/main/java/com/dhmeter/domain/model/MapModels.kt`: `GpsPoint` no tiene campo de altitud.
- `signal/src/main/java/com/dhmeter/signal/processor/GpsPolylineProcessor.kt`: construye `GpsPoint` solo con `lat/lon/distPct`.
- `data/src/main/java/com/dhmeter/data/mapper/GpsPolylineMapper.kt`: serializa solo `lat/lon/distPct`.

Impacto practico: hoy puedes ubicar eventos en mapa, pero no puedes construir perfil de elevacion del descenso con los datos guardados.

## 3) Opciones sin pago / open source

### Opcion A - Solo altitud del GPS del telefono (100% local)
Pros:
- Cero costo.
- Funciona offline.
- Sin dependencia externa.

Contras:
- Ruido alto vertical en GPS (puede variar bastante punto a punto).
- Necesita suavizado para verse util.

Uso recomendado:
- Base minima inmediata para mostrar perfil de descenso.

### Opcion B - OpenTopoData (recomendada)
Modelo:
- API publica gratis o self-host.
- Open source (MIT).
- Datasets abiertos (SRTM, NED, etc.).

Pros:
- Permite mejorar/corregir altitud GPS con DEM.
- Puede auto-hospedarse para eliminar limites de tercero.
- Tiene soporte para polylines y muestreo sobre ruta.

Contras:
- API publica tiene limites.
- Self-host requiere operacion de servidor.

Limites de API publica (sitio oficial):
- 100 locations/request
- 1 call/second
- 1000 calls/day

### Opcion C - Open-Elevation
Modelo:
- Open source (GPLv2), hosteable.
- API publica con plan gratis limitado.

Pros:
- Muy simple de integrar.
- Se puede hostear propio.

Contras:
- API publica limitada para app en crecimiento.
- Self-host implica preparar datasets (documentan tamanos grandes).

### Opcion D - Open-Meteo Elevation API
Pros:
- Simple y rapido de integrar.
- Datos DEM de 90m (Copernicus GLO-90).

Contras critico para app comercial:
- Free API es solo uso no comercial segun sus terminos.
- Si hay monetizacion, normalmente pasas a plan pago.

## 4) Recomendacion para esta app

Por tu contexto (app con monetizacion), la mejor ruta tecnica/legal es:
1. Implementar perfil de altitud local inmediato con GPS (sin costo).
2. Agregar "correccion DEM opcional" con OpenTopoData self-hosted (MIT).
3. Mantener fallback offline al GPS cuando no haya red/servicio.

Esto te da:
- UX inmediata.
- Escalabilidad sin lock-in.
- Control de costo.

## 5) Cambios tecnicos necesarios en tu codigo

### Minimo para que ya funcione perfil de altitud
1. Agregar `altitudeM: Float?` en `GpsPoint`.
2. En `GpsPolylineProcessor`, mapear `sample.altitude` -> `GpsPoint.altitudeM`.
3. Actualizar `GpsPolylineMapper` para serializar/deserializar altitud.
4. En `RunMapData`, agregar un perfil de elevacion (o derivarlo de `polyline.points`).
5. En `MapScreen`, agregar vista de perfil (mini grafica o capa).

### Compatibilidad de datos viejos
- Se puede mantener sin migracion SQL si el blob de puntos se parsea con formato versionado o deteccion por tamano (20 bytes viejo vs nuevo formato).
- Asi las corridas antiguas siguen abriendo.

## 6) Metricas de descenso que puedes desbloquear

Con altitud ya propagada:
- desnivel negativo total (m)
- desnivel positivo total (m)
- pendiente media (%)
- pendiente maxima (%) por tramo
- mapa de pendiente por color

## 7) Riesgos y mitigacion

Riesgo: ruido de altitud GPS.
Mitigacion: suavizado (mediana + EMA) y umbral minimo por delta vertical.

Riesgo: limites de API publica.
Mitigacion: cache por `runId` y/o self-host de OpenTopoData.

Riesgo: atribucion/licencias.
Mitigacion: agregar creditos en pantalla de ajustes/info segun proveedor.

## 8) Fuentes oficiales usadas

- Open-Meteo Elevation API: https://open-meteo.com/en/docs/elevation-api
- Open-Meteo Terms: https://open-meteo.com/en/terms
- Open-Meteo Licence: https://open-meteo.com/en/licence
- OpenTopoData home/docs: https://www.opentopodata.org/
- OpenTopoData API docs: https://www.opentopodata.org/api/
- OpenTopoData licencia MIT: https://raw.githubusercontent.com/ajnisbet/opentopodata/master/LICENSE.md
- Open-Elevation sitio: https://www.open-elevation.com/
- Open-Elevation repo: https://github.com/Jorl17/open-elevation
- Open-Elevation API docs (raw): https://raw.githubusercontent.com/Jorl17/open-elevation/master/docs/api.md
- Open-Elevation host-your-own (raw): https://raw.githubusercontent.com/Jorl17/open-elevation/master/docs/host-your-own.md
- Open-Elevation licencia GPLv2 (raw): https://raw.githubusercontent.com/Jorl17/open-elevation/master/license.md
