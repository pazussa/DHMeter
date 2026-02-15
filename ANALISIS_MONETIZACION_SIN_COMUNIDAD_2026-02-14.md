# Analisis de monetizacion para dropIn DH (sin comunidad)

Fecha: 2026-02-14  
Proyecto: dropIn DH (DHMeter)  
Alcance: evaluacion de modelos de monetizacion asumiendo **eliminacion total del modulo de comunidad**.

---

## 1) Resumen ejecutivo

### Que es dropIn DH

App Android de **telemetria para downhill mountain biking**. El rider coloca el telefono en el bolsillo, graba una bajada, y la app procesa datos de acelerometro, giroscopio y GPS para entregar:

- **Puntajes** de impacto, aspereza (harshness), estabilidad y velocidad.
- **Graficas** comparativas de multiples bajadas alineadas por distancia.
- **Mapa** con heatmap de severidad y marcadores de eventos (aterrizajes, impactos).
- **Deteccion de eventos** (saltos, aterrizajes, picos de impacto).
- **Comparacion multi-run** con veredicto y analisis por secciones.

### Que cambia al quitar comunidad

| Aspecto | Con comunidad | Sin comunidad |
|---|---|---|
| Backend requerido | Firebase Auth + Firestore | Ninguno (100% local) |
| Costo operativo | Moderacion, infra cloud, UGC compliance | Casi cero |
| Complejidad de compliance Play | Alta (UGC, account deletion, ToS) | Baja |
| Features sociales | Chat, rankings, retos | No existen |
| Valor de suscripcion recurrente | Mas justificable (servicios cloud) | Mas dificil de justificar |

**Impacto clave**: sin comunidad, la app es 100% local y offline. Esto **reduce drasticamente los costos operativos** pero tambien **debilita el argumento para cobro recurrente** (suscripcion), ya que no hay servicios cloud que mantener.

### Recomendacion

**Modelo hibrido: Freemium con pago unico como eje principal + suscripcion Pro opcional.**

Justificacion resumida:
- Sin backend cloud, el costo marginal por usuario es ~0 → pago unico es sostenible.
- Suscripcion pura es dificil de justificar sin servicios recurrentes.
- Anuncios degradan la experiencia en una app deportiva de rendimiento.
- El hibrido captura ambos segmentos: quienes prefieren pagar una vez y quienes valoran features premium recurrentes.

---

## 2) Perfil del producto sin comunidad

### Features actuales (lo que queda)

| Categoria | Features |
|---|---|
| **Grabacion** | Recording con sensores IMU + GPS, preview en vivo, auto-start/stop por segmento GPS, servicio foreground |
| **Procesamiento** | Pipeline DSP: deteccion de impactos, analisis de aspereza, indice de estabilidad, deteccion de aterrizajes, validacion de run |
| **Metricas** | Impact Score, Harshness RMS (avg/P90), Stability Index, Landing Quality, Speed (avg/max), distancia, duracion |
| **Visualizacion** | Graficas de linea comparativas, heatmaps de severidad, marcadores de eventos, timeline |
| **Mapa** | Polyline GPS con overlay de severidad (4 metricas), marcadores de eventos, comparacion por secciones |
| **Comparacion** | 2-run y multi-run con veredictos, deltas por metrica, split-time por seccion |
| **Configuracion** | Sensibilidad de sensores (4 sliders), idioma (ES/EN), auto-start por track |
| **Almacenamiento** | Room DB local (tracks, runs, series, eventos, polylines GPS) |

### Caracteristicas tecnicas relevantes para monetizacion

- **100% offline** (sin comunidad): no hay costo de servidor, no hay sincronizacion cloud.
- **Procesamiento local**: todo el DSP corre en el dispositivo.
- **Sin cuenta de usuario**: no se requiere registro ni autenticacion.
- **Datos solo en dispositivo**: el usuario es dueño de todos sus datos.

---

## 3) Evaluacion de los tres modelos de monetizacion

### 3.1 Modelo A: Anuncios (Ads)

#### Como funcionaria

- Banners en pantallas de historial/resumen.
- Interstitials entre grabaciones o al ver comparaciones.
- Rewarded ads para desbloquear features temporalmente.

#### Ventajas

| Ventaja | Detalle |
|---|---|
| Barrera de entrada cero | Todos los usuarios acceden a todo |
| Ingreso desde el dia 1 | No depende de conversion a pago |
| Sin friccion de paywall | No hay momento de rechazo |

#### Desventajas

| Desventaja | Detalle | Severidad |
|---|---|---|
| **Experiencia degradada** | Banners/interstitials durante analisis de rendimiento rompen el flujo profesional | **CRITICA** |
| **Ingresos muy bajos** | eCPM tipico en apps deportivas nicho: $1-4 USD por 1,000 impresiones | **ALTA** |
| **Percepcion de calidad baja** | Apps con ads se perciben como "menos serias" en nicho deportivo | **ALTA** |
| **Audiencia pequeña** | Con <50k MAU, los ingresos por ads son insignificantes | **ALTA** |
| **Conflicto con grabacion** | No se puede mostrar ads durante una bajada (telefono en bolsillo) | **MEDIA** |
| **Privacidad y tracking** | AdMob requiere tracking del usuario, mas politicas de privacidad | **MEDIA** |

#### Proyeccion financiera (ads)

| MAU | Impresiones/usuario/mes | Total impresiones | eCPM | Ingreso bruto/mes |
|---:|---:|---:|---:|---:|
| 5,000 | 30 | 150,000 | $2.50 | $375 |
| 10,000 | 30 | 300,000 | $2.50 | $750 |
| 50,000 | 30 | 1,500,000 | $2.50 | $3,750 |

Nota: 30 impresiones/usuario/mes es optimista para una app de telemetria donde el usuario graba 2-5 bajadas/semana y revisa resultados brevemente.

#### Veredicto: NO RECOMENDADO como modelo principal

Los anuncios no funcionan bien para dropIn DH porque:
- La audiencia es demasiado nicho y pequeña para generar volumen.
- La experiencia de analisis de rendimiento se ve severamente afectada.
- El eCPM en apps deportivas nicho es bajo.
- No hay momentos naturales de "espera" donde un ad sea tolerable (la grabacion es en background, el analisis es rapido).

**Unico escenario valido**: ads no intrusivos como patrocinios de marca (ej. "analisis patrocinado por Fox Suspension") si se consiguen deals directos B2B. Esto es marketing, no ads programaticos.

---

### 3.2 Modelo B: Suscripcion (mensual/anual)

#### Como funcionaria

- **Free**: grabacion + resumen basico + historial limitado (ej. 5 ultimas bajadas).
- **Pro mensual**: ~$6.99-7.99 USD/mes.
- **Pro anual**: ~$49.99-59.99 USD/año (descuento ~40%).
- Trial de 7 dias.

#### Ventajas

| Ventaja | Detalle |
|---|---|
| Ingreso recurrente predecible | MRR/ARR acumulable |
| Alto LTV por usuario | Un suscriptor de 12 meses = $50-60 USD |
| Alineacion de incentivos | El desarrollador sigue mejorando para retener |
| Estandar de la industria | Strava, Komoot, TrainerRoad usan este modelo |

#### Desventajas (especificas SIN comunidad)

| Desventaja | Detalle | Severidad |
|---|---|---|
| **Dificil justificar pago recurrente** | Sin cloud/sync/comunidad, el usuario pregunta "por que pago cada mes si todo es local?" | **CRITICA** |
| **Churn alto** | Sin servicios recurrentes, usuarios cancelan al sentir que "ya tienen todo" | **ALTA** |
| **Fatiga de suscripciones** | El mercado tiene saturacion de suscripciones; riders ya pagan Strava, Trailforks, etc. | **ALTA** |
| **Conversion inicial baja** | Benchmarks: 1.5-2.7% download-to-paid (RevenueCat 2025) | **MEDIA** |
| **Complejidad tecnica** | Billing, validacion, entitlement, edge cases (cancelaciones, pausas, reembolsos) | **MEDIA** |

#### Proyeccion financiera (suscripcion)

Usando Celda A ($6.99/mes, $49.99/año), mix 70% anual / 30% mensual:

| MAU | Conversion | Pagadores | Neto/mes (fee 15%) |
|---:|---:|---:|---:|
| 5,000 | 2% | 100 | $426 |
| 10,000 | 3% | 300 | $1,278 |
| 25,000 | 3% | 750 | $3,196 |
| 50,000 | 4% | 2,000 | $8,522 |

#### Problema central sin comunidad

El problema fundamental es: **que servicio recurrente justifica el cobro mensual?**

Con comunidad, la respuesta es: cloud sync, moderacion, infraestructura social.  
Sin comunidad, la respuesta es: nuevas features y actualizaciones... pero eso no es un "servicio" que el usuario perciba como continuo.

Apps 100% locales con suscripcion exitosa existen (ej. editores de foto, apps de productividad), pero suelen tener:
- Actualizaciones muy frecuentes de contenido (filtros, plantillas).
- Uso diario (no semanal como DH).
- Audiencia masiva (millones de MAU).

dropIn DH no tiene ninguna de esas tres caracteristicas.

#### Veredicto: VIABLE PERO RIESGOSO como modelo unico sin comunidad

La suscripcion funciona mejor como **capa premium opcional**, no como modelo unico, porque el argumento de valor recurrente es debil sin servicios cloud. Usar como modelo principal solo si se planea agregar features recurrentes a futuro (cloud sync, AI analysis, coaching insights automaticos).

---

### 3.3 Modelo C: Pago unico (lifetime/one-time)

#### Como funcionaria

- **Free**: grabacion + resumen basico + historial limitado.
- **Pro Lifetime**: pago unico de $24.99-34.99 USD → desbloquea todo para siempre.

#### Ventajas

| Ventaja | Detalle |
|---|---|
| **Coherente con producto local** | "Pagas una vez, todo es tuyo" tiene sentido para app sin cloud | 
| **Conversion mas alta** | Usuarios anti-suscripcion (muchos) convierten aqui |
| **Sin churn** | Una vez comprado, no hay cancelaciones |
| **Simple de implementar** | Un SKU INAPP, sin logica de renovacion/cancelacion |
| **Sin costo operativo por usuario** | App local = costo marginal ~0 |
| **Percepcion de valor justo** | Riders pagan $30+ por un grip de manubrio; una app de telemetria a $30 es razonable |

#### Desventajas

| Desventaja | Detalle | Severidad |
|---|---|---|
| **Ingreso no recurrente** | Depende de nuevos compradores cada mes | **ALTA** |
| **LTV limitado** | Un comprador = $21-30 USD netos, fin | **ALTA** |
| **Techo de ingreso** | En audiencia nicho, el pool de compradores se agota | **MEDIA** |
| **Menor incentivo para mejorar** | Sin ingreso recurrente, el ROI de mejoras baja | **MEDIA** |

#### Proyeccion financiera (pago unico)

Al precio de $29.99 USD (neto $25.49 tras fee 15%):

| Installs/mes | Conversion | Compras/mes | Neto/mes |
|---:|---:|---:|---:|
| 2,000 | 3% | 60 | $1,529 |
| 5,000 | 3% | 150 | $3,824 |
| 10,000 | 4% | 400 | $10,196 |

Nota: la conversion a pago unico suele ser ligeramente mas alta que a suscripcion (menos friccion psicologica de compromiso recurrente).

#### Veredicto: MUY VIABLE como modelo principal para app sin comunidad

El pago unico es la opcion mas natural para una app 100% local sin backend. La narrativa comercial es clara: "tu herramienta de telemetria, pagas una vez, tuya para siempre."

---

## 4) Modelo recomendado: Hibrido (pago unico + suscripcion opcional)

### Estructura

```
┌─────────────────────────────────────────────────────────┐
│                        FREE                             │
│  • Grabacion ilimitada de bajadas                       │
│  • Resumen basico (puntaje global, velocidad)           │
│  • Historial de ultimas 3 bajadas                       │
│  • Mapa basico (polyline sin heatmap)                   │
│  • 1 comparacion por track                              │
└──────────────────────┬──────────────────────────────────┘
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
┌─────────────────────┐   ┌─────────────────────────────┐
│   CORE (lifetime)   │   │      PRO (suscripcion)      │
│   $24.99 - 29.99    │   │  $5.99/mes  ·  $39.99/año   │
│   Pago unico        │   │  Todo Core +                │
│                     │   │                             │
│  • Historial        │   │  • Cloud sync/backup        │
│    ilimitado        │   │    (cuando exista)          │
│  • Comparaciones    │   │  • Exportacion CSV/GPX      │
│    multi-run        │   │  • Analisis de tendencias   │
│  • Mapa con heatmap │   │    entre temporadas         │
│    (4 metricas)     │   │  • AI insights (futuro)     │
│  • Todos los eventos│   │  • Soporte prioritario      │
│  • Sensibilidad     │   │                             │
│    configurable     │   │                             │
│  • Graficas overlay │   │                             │
│    de comparacion   │   │                             │
└─────────────────────┘   └─────────────────────────────┘
```

### Por que este modelo y no otro

| Razon | Explicacion |
|---|---|
| **Coherencia con arquitectura** | App 100% local → pago unico tiene sentido logico |
| **Captura ambos segmentos** | Anti-suscripcion → Core. Power users → Pro |
| **Baja complejidad operativa** | Sin backend cloud, el pago unico no genera costo por usuario |
| **Escalable** | Si en el futuro agregas cloud/AI, Pro ya existe como capa |
| **Conversion mas alta** | El pago unico convierte mejor que suscripcion en nicho deportivo |
| **Precio competitivo** | $25-30 es menos que un dia de bikepark y menos que cualquier componente de bici |

### Pricing recomendado

#### Core Lifetime

| Opcion | Precio | Neto (fee 15%) | Contexto de valor |
|---|---:|---:|---|
| Conservador | $24.99 | $21.24 | Precio de 2 cafes especiales |
| **Recomendado** | $29.99 | $25.49 | Precio de un par de grips o un set de pastillas de freno |
| Agresivo | $34.99 | $29.74 | Precio de un protector de cuadro |

#### Pro Suscripcion (opcional, para futuro)

| Plan | Precio | Neto (fee 15%) |
|---|---:|---:|
| Mensual | $5.99 | $5.09 |
| Anual | $39.99 | $34.00 |

Descuento anual vs mensual: ~44%.

**Importante**: La suscripcion Pro tiene sentido activarla **solo cuando existan features recurrentes reales** (cloud sync, exportaciones avanzadas, AI insights). No lanzarla vacia — daña la confianza.

### Estrategia de lanzamiento

**Fase 1 (lanzamiento): Solo Free + Core Lifetime**

- Lanzar con 2 tiers: Free y Core Lifetime.
- Sin suscripcion todavia (no hay features recurrentes que la justifiquen).
- Focus: conversion de Free a Core.
- Paywall: mostrar despues de la 3ra bajada completada (momento de valor demostrado).

**Fase 2 (3-6 meses post-lanzamiento): Evaluar suscripcion**

- Si hay demanda de features cloud (sync, export, tendencias), implementar Pro.
- Si la demanda no existe, mantener solo Free + Core y seguir iterando.

**Fase 3 (6-12 meses): Optimizar**

- A/B test de precios Core.
- Introducir ofertas estacionales (inicio de temporada DH, post-eventos).
- Evaluar bundles (Core + extension futura).

---

## 5) Que features gatear (bloquear en Free)

### Criterio de gating

El Free debe ser **suficientemente util para enganchar**, pero **limitado en profundidad** para motivar la compra.

La linea de corte: el usuario Free puede **grabar y ver un resumen basico**, pero no puede **analizar en profundidad ni comparar**.

### Tabla de gating

| Feature | Free | Core (lifetime) |
|---|:---:|:---:|
| Grabacion de bajadas | ✅ Ilimitada | ✅ Ilimitada |
| Resumen con puntaje global | ✅ | ✅ |
| Velocidad (avg/max) | ✅ | ✅ |
| Distancia y duracion | ✅ | ✅ |
| Historial de bajadas | ⚠️ Ultimas 3 | ✅ Ilimitado |
| Puntajes por metrica (Impact, Harshness, Stability) | ⚠️ Solo puntaje, sin detalle | ✅ Con detalle y clasificacion |
| Mapa con polyline | ✅ Basico (sin color/heatmap) | ✅ Con heatmap de severidad |
| Seleccion de metrica en mapa (4 metricas) | ❌ | ✅ |
| Graficas de comparacion (overlay charts) | ❌ | ✅ |
| Comparacion multi-run | ⚠️ 1 comparacion basica | ✅ Ilimitada con veredictos |
| Comparacion por secciones (split-time) | ❌ | ✅ |
| Deteccion de eventos (landings, impacts) | ⚠️ Conteo solamente | ✅ Lista completa con detalle |
| Configuracion de sensibilidad de sensores | ❌ (defaults) | ✅ |
| Auto-start/stop por GPS | ❌ | ✅ |

### Por que este corte y no otro

- **Grabacion siempre gratis**: la propuesta de valor inicial (grabar bajadas) debe ser libre para maximizar adopcion y engagement.
- **Historial limitado a 3**: suficiente para probar, insuficiente para analizar progreso. El rider que vuelve por 4ta vez ya demostro interes → momento natural de conversion.
- **Mapa basico gratis**: ver la ruta incentiva el deseo de ver el heatmap de severidad → trigger de compra.
- **Comparacion limitada a 1**: probar la comparacion demuestra el valor; querer comparar mas motiva la compra.
- **Sensibilidad bloqueada**: el rider avanzado que quiere calibrar su setup es el perfil ideal de pagador.

---

## 6) Comparativa financiera de los 3 modelos a 12 meses

### Supuestos comunes

- Installs acumulados en 12 meses: 30,000 (2,500/mes promedio).
- MAU estabilizado al mes 12: ~10,000.
- Fee de Play: 15%.

### Escenario: Anuncios (solo ads)

| Metrica | Valor |
|---|---|
| MAU promedio | 7,000 |
| Impresiones/usuario/mes | 25 |
| eCPM | $2.50 |
| Ingreso bruto mensual promedio | $438 |
| **Ingreso neto 12 meses** | **~$5,250** |

### Escenario: Suscripcion pura

| Metrica | Valor |
|---|---|
| Conversion download-to-paid | 2.5% |
| Pagadores acumulados mes 12 | ~450 (con churn ~8%/mes → ~300 activos) |
| Neto por suscriptor/mes | $4.26 (Celda A) |
| Ingreso neto mensual mes 12 | ~$1,278 |
| **Ingreso neto acumulado 12 meses** | **~$9,200** |

### Escenario: Pago unico ($29.99)

| Metrica | Valor |
|---|---|
| Conversion download-to-paid | 3.5% |
| Compras en 12 meses | ~1,050 |
| Neto por compra | $25.49 |
| **Ingreso neto acumulado 12 meses** | **~$26,765** |

### Escenario: Hibrido (pago unico + suscripcion opcional)

| Metrica | Valor |
|---|---|
| Conversion a Core (lifetime) | 3% |
| Compras Core en 12 meses | ~900 |
| Neto Core | $22,941 |
| Conversion a Pro (si existe) | 0.5% |
| Suscriptores Pro activos mes 12 | ~50 |
| Neto Pro acumulado | ~$2,040 |
| **Ingreso neto total 12 meses** | **~$24,981** |

### Resumen comparativo

| Modelo | Ingreso neto 12 meses | Ingreso recurrente | Complejidad | Experiencia usuario |
|---|---:|:---:|:---:|:---:|
| Anuncios | ~$5,250 | No | Baja | Degradada |
| Suscripcion | ~$9,200 | Si | Alta | Buena |
| Pago unico | ~$26,765 | No | Baja | Excelente |
| **Hibrido** | **~$24,981** | Parcial | Media | **Excelente** |

**Lectura**: el pago unico genera ~3x mas ingreso que suscripcion y ~5x mas que anuncios en el primer año con la misma base de usuarios. Esto se debe a que:
1. La conversion a pago unico es mayor (menor friccion psicologica).
2. No hay churn (no se pierde el ingreso de suscriptores que cancelan).
3. El precio por compra ($29.99) es competitivo para el nicho.

---

## 7) Analisis de riesgos por modelo

### Anuncios

| Riesgo | Probabilidad | Impacto | Mitigacion |
|---|:---:|:---:|---|
| Ingreso insuficiente para sostener desarrollo | Alta | Critico | No usar como modelo principal |
| Rechazo de usuarios por ads en app deportiva | Alta | Alto | Solo ads no intrusivos / patrocinios |
| eCPM bajo por audiencia nicho | Alta | Alto | Sin mitigacion efectiva |

### Suscripcion (sin comunidad)

| Riesgo | Probabilidad | Impacto | Mitigacion |
|---|:---:|:---:|---|
| Churn alto por falta de valor recurrente percibido | Alta | Critico | Agregar features recurrentes reales |
| "Por que pago mensual si todo es local?" | Alta | Alto | Comunicar roadmap de features cloud |
| Fatiga de suscripciones (riders ya pagan Strava) | Media | Alto | Precio competitivo + trial |
| Baja conversion inicial | Media | Medio | Paywall timing optimizado |

### Pago unico

| Riesgo | Probabilidad | Impacto | Mitigacion |
|---|:---:|:---:|---|
| Agotamiento del pool de compradores | Media | Alto | Expandir audiencia + features nuevas |
| Sin ingreso recurrente para sostenibilidad | Media | Alto | Combinar con Pro opcional |
| Pirateria/sharing de APK | Baja | Medio | Validacion local robusta |

### Hibrido (recomendado)

| Riesgo | Probabilidad | Impacto | Mitigacion |
|---|:---:|:---:|---|
| Canibalizacion (Core come a Pro) | Media | Medio | Diferenciar features claramente |
| Complejidad de 3 tiers | Baja | Bajo | UI clara en paywall |
| Confusion del usuario | Baja | Bajo | Comunicacion simple de beneficios |

---

## 8) Momento optimo del paywall

### Principio: mostrar el paywall despues del primer momento "aha"

Para dropIn DH, el momento "aha" es: **el rider ve los resultados de su primera bajada y quiere saber mas**.

### Flujo recomendado

```
Instala → Graba 1ra bajada → Ve resumen basico (GRATIS)
       → Graba 2da bajada → Ve resumen basico (GRATIS)
       → Graba 3ra bajada → Ve resumen basico (GRATIS)
       → Intenta ver 4ta bajada en historial → PAYWALL
       → Intenta comparar 2 bajadas con detalle → PAYWALL
       → Intenta ver heatmap en mapa → PAYWALL
```

### Triggers de paywall (no intrusivo)

| Trigger | Momento | Tipo |
|---|---|---|
| Historial lleno | Al intentar ver bajada #4 | Bloqueo suave con preview |
| Comparacion avanzada | Al intentar 2da comparacion | Bloqueo con muestra de lo que veria |
| Heatmap de mapa | Al tocar selector de metrica en mapa | Bloqueo con preview borroso |
| Sensibilidad de sensores | Al abrir panel de sensibilidad | Bloqueo suave |
| Eventos detallados | Al tocar evento en lista | Muestra conteo, bloquea detalle |

### Anti-patron: NO hacer

- No mostrar paywall antes de la primera bajada.
- No mostrar paywall fullscreen al abrir la app.
- No bloquear la grabacion (siempre gratis).
- No mostrar ads como alternativa al pago.

---

## 9) Implementacion tecnica requerida

### Estado actual del codigo

Ya implementado:
- `BillingManager.kt` — Google Play Billing para suscripciones.
- `MonetizationCatalog.kt` — SKUs `pro_monthly`, `pro_yearly`.
- `ProScreen.kt` + `ProViewModel.kt` — Paywall UI.
- `EventTracker.kt` — Funnel de monetizacion.
- `PurchaseValidator.kt` — Validacion local.
- Flag `isPro` en SharedPreferences.

### Cambios necesarios para modelo hibrido

| Cambio | Archivo(s) | Esfuerzo |
|---|---|---|
| Agregar SKU `core_lifetime` | `MonetizationCatalog.kt` | Bajo |
| Soporte `ProductType.INAPP` en billing | `BillingManager.kt` | Medio |
| Fusionar entitlement (lifetime + subscription) | `BillingManager.kt` | Medio |
| Actualizar paywall con opcion lifetime | `ProScreen.kt` | Medio |
| Implementar gating de features | ViewModels de cada pantalla | Medio-Alto |
| Nuevos eventos de tracking | `EventTracker.kt` | Bajo |
| Eliminar modulo de comunidad | Multiples archivos | Medio |

Esfuerzo total estimado: **2-3 semanas** de desarrollo.

### Gating tecnico

Agregar a cada ViewModel un check de entitlement:

```kotlin
// En el ViewModel correspondiente
val hasAccess: StateFlow<Boolean> = billingManager.entitlementState
    .map { it.isCorePlus } // true si tiene Core Lifetime o Pro
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)
```

Niveles de entitlement:
- `FREE` — sin compra activa.
- `CORE` — compra lifetime activa.
- `PRO` — suscripcion activa (incluye todo de Core).

---

## 10) KPIs de seguimiento

### Metricas semanales

| KPI | Meta inicial | Descripcion |
|---|---|---|
| Installs/semana | 500+ | Volumen de adquisicion |
| Bajadas completadas / install | ≥2.0 | Engagement pre-paywall |
| Paywall view rate | ≥40% de instaladores | Cuantos llegan al trigger |
| Conversion paywall → compra | ≥5% de views | Efectividad del paywall |
| Conversion install → paid | ≥3% | Conversion global |
| Revenue/install | ≥$0.75 | Monetizacion por install |

### Metricas mensuales

| KPI | Meta | Descripcion |
|---|---|---|
| Compras Core/mes | ≥50 | Flujo de ingresos |
| Ingreso neto/mes | ≥$1,250 | Sostenibilidad |
| D7 retention | ≥30% | Stickiness |
| D30 retention | ≥15% | Valor a largo plazo |
| Rating Play Store | ≥4.3 | Calidad percibida |

### Regla de decision (90 dias post-lanzamiento)

- Si conversion install → paid ≥3% y ingreso neto ≥$1,000/mes: **mantener modelo y optimizar**.
- Si conversion <2% pero engagement alto: **ajustar paywall timing/messaging y re-evaluar en 30 dias**.
- Si conversion <1% y engagement bajo: **revisar propuesta de valor y features gateadas**.

---

## 11) Contexto competitivo

### Apps comparables y sus modelos

| App | Modelo | Precio | Que mide | Modelo similar a dropIn DH |
|---|---|---|---|:---:|
| Strava | Suscripcion | $11.99/mes, $79.99/año | Rendimiento general ciclismo/running | Parcial |
| Trailforks | Suscripcion (via Outside+) | ~$4.49/mes | Mapas de trails MTB | No |
| Komoot | Hibrido (regiones + premium) | Regiones $3.99 one-time + Premium $59.99/año | Navegacion/rutas | Parcial |
| Trail Forks | Freemium | Gratis + Inside Line | Trails/mapas | No |
| Bike Computer (DS) | Pago unico + IAP | $4.99-14.99 | GPS/velocidad/altimetria | Si |
| RideWithGPS | Suscripcion | $6/mes o $50/año | Rutas + navegacion | Parcial |

### Diferenciacion clave de dropIn DH

Ninguna app del mercado ofrece:
- Analisis de **calidad de bajada** con IMU (impacto, aspereza, estabilidad).
- **Deteccion de aterrizajes** con airtime y severidad.
- **Comparacion multi-run** alineada por distancia con veredictos.
- **Heatmaps de severidad** por 4 metricas distintas.

Esto posiciona a dropIn DH como herramienta especializada, no como competidor directo de Strava. El pago unico refuerza esta posicion: "es una herramienta, la compras y es tuya."

---

## 12) Recomendacion final

### Modelo: Free + Core Lifetime

| Aspecto | Decision |
|---|---|
| **Modelo principal** | Pago unico (Core Lifetime) a $29.99 USD |
| **Modelo secundario** | Suscripcion Pro (activar solo cuando haya features recurrentes reales) |
| **Ads** | No usar. Solo considerar patrocinios B2B directos en fase madura |
| **Paywall timing** | Despues de 3 bajadas completadas |
| **Trial** | No aplica en pago unico (el Free ya funciona como trial) |
| **Feature gating** | Historial, comparaciones avanzadas, heatmaps, sensibilidad, auto-start |

### Plan de ejecucion inmediato

1. **Eliminar modulo de comunidad** (Firebase Auth, Firestore, UGC compliance).
2. **Agregar SKU `core_lifetime`** al catalogo de billing.
3. **Implementar gating de features** en los ViewModels.
4. **Actualizar paywall** para mostrar opcion lifetime.
5. **Configurar producto en Google Play Console** (INAPP product).
6. **Lanzar con Free + Core Lifetime** unicamente.
7. **Medir durante 90 dias** antes de decidir sobre Pro suscripcion.

### Por que NO suscripcion como modelo principal (sin comunidad)

La suscripcion como modelo unico falla en este contexto porque:

1. **No hay costo operativo recurrente** que justifique cobro mensual (todo es local).
2. **El usuario lo percibe**: "si mis datos estan en mi telefono y no uso servidor, por que pago cada mes?"
3. **Churn alto**: sin features cloud que "pierde" al cancelar, el rider no teme cancelar.
4. **Saturacion de suscripciones**: el rider ya paga Strava y quizas Trailforks. Otra suscripcion es dificil de vender.
5. **El nicho es pequeño**: con 2-3% de conversion y churn de 8%, la base activa crece lentamente.

El pago unico resuelve todos estos problemas: coherente con producto local, conversion mayor, sin churn, precio competitivo para el nicho.

---

*Documento generado el 2026-02-14. Basado en analisis del codigo fuente de dropIn DH, benchmarks de mercado (RevenueCat 2024-2025, Strava, Komoot), y datos de Android market share (StatCounter ene-2026).*
