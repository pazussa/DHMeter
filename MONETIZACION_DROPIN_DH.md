# Estrategia de monetizacion para dropIn DH

Fecha de analisis: 2026-02-12  
Alcance: estrategia comercial + plan de ejecucion para iniciar monetizacion en Google Play.

## 1) Resumen ejecutivo

La mejor estrategia para dropIn DH es **freemium con suscripcion Pro** como eje principal, y una segunda capa B2B (coaches/clubes) cuando exista backend cloud.

Por que esta es la mejor ruta para esta app:
- El valor real de la app esta en datos historicos, comparaciones y mejora de rendimiento (alto valor recurrente, no solo uso puntual).
- El publico (riders entrenando) acepta mejor pago por utilidad de performance que por publicidad.
- La publicidad intrusiva reduce confianza en apps deportivas y afecta experiencia en terreno.

Recomendacion concreta:
- Modelo inicial: `Free + Pro mensual/anual`.
- Monetizacion secundaria (fase 2): `Coach/Club plan`.
- Evitar ads al inicio. Si se prueban, solo patrocinios no intrusivos (sin banners persistentes en recording).

---

## 2) Diagnostico actual del proyecto (tecnico/comercial)

Estado observado en el repo hoy:
- `app/build.gradle.kts` usa `targetSdk = 34` y `compileSdk = 34`.
- No se ve integrada la libreria de billing de Google Play.
- Ya existe base funcional de producto (recording, charts, comparaciones, comunidad basica), por lo que monetizar por features avanzadas es viable.

Implicacion clave para publicar/monetizar:
- Para nuevas versiones en Play, hoy debes apuntar a API 35 para cumplir ventanas actuales de publicacion.

---

## 3) Modelo de negocio recomendado

## 3.1 Free (adquisicion)

Objetivo: volumen y activacion.

Dejar gratis:
- Grabacion de bajadas.
- Mapa y resumen basico.
- Comunidad basica (lectura + chat limitado).

Limitar en free:
- Historial profundo (ej. solo ultimas 5-10 bajadas completas).
- Comparaciones multi-run avanzadas.
- Exportaciones (CSV/GPX avanzadas).
- Insights avanzados por seccion y tendencias.

## 3.2 Pro (conversion)

Objetivo: ingreso recurrente (MRR/ARR).

Pro debe incluir:
- Historial ilimitado y backup/sync cloud.
- Comparaciones avanzadas por secciones.
- Analitica de progreso (impacto, vibracion, inestabilidad, velocidad) con tendencias.
- Exportacion de datos para coaching.
- Herramientas premium de comunidad (grupos privados, rankings por zonas, retos).

## 3.3 B2B (expansion)

Objetivo: aumentar ARPU y bajar dependencia de conversion individual.

Oferta:
- Panel coach/club con gestion de riders.
- Reportes por equipo y por seccion.
- Paquetes por cantidad de riders.

Nota: B2B conviene activarlo cuando haya backend robusto (auth cloud, permisos y facturacion empresarial).

---

## 4) Estrategia de precios inicial (practica)

## 4.1 Anclas de mercado (referencia)

Referencias publicas recientes:
- Strava en US: `11.99 USD/mes` o `79.99 USD/anio` (update 2025-07-01).
- Trailforks Pro muestra plan aprox `4.49 USD/mes` (pago anual dentro de ecosistema Outside).
- Komoot combina free + compras one-time + premium anual.

Lectura de mercado:
- El rango util para dropIn DH esta en banda media de apps deportivas premium.
- Para primera etapa, conviene entrar con precio competitivo frente a Strava pero suficiente para sostener soporte y desarrollo.

## 4.2 Precio recomendado para iniciar test

Lanzar dos celdas (A/B):
- Celda A: `6.99 USD/mes` y `49.99 USD/anio`.
- Celda B: `7.99 USD/mes` y `59.99 USD/anio`.

Regla comercial:
- Anual con descuento efectivo de 35-45% vs mensual.
- Ofrecer trial corto (ej. 7 dias) o intro price de onboarding.

## 4.3 Unit economics simple (escenario base)

Supuestos:
- 10,000 MAU
- conversion paid: 3%
- mix paid: 70% anual, 30% mensual
- precio: 49.99 anual / 6.99 mensual

Resultado aproximado:
- Paid users: 300
- Ingreso bruto mensual equivalente por paid: ~5.01 USD
- Ingreso bruto total mensual: ~1,504 USD
- Neto tras fee Play 15%: ~1,278 USD/mes (antes de impuestos y costos operativos)

Lectura:
- Con 10k MAU ya existe base para sostener desarrollo pequeño.
- El verdadero salto vendra de:
  - mayor conversion a Pro
  - anualidad alta
  - ticket B2B coach/club

---

## 5) Como empezar (plan operativo 0-90 dias)

## Fase 0 (semana 1-2): base de monetizacion

1. Preparar compliance de release:
- Subir target/compile a API 35.
- Revisar Data safety y disclosure de ubicacion/sensores (incluido background).
- Definir privacidad y borrado de cuenta (si cuentas cloud).

2. Definir catalogo comercial:
- `pro_monthly`, `pro_yearly`.
- 1 oferta de prueba para adquisicion.

3. Medicion minima:
- eventos: install, onboarding_complete, first_run_complete, paywall_view, trial_start, purchase_success, churn.

## Fase 1 (semana 3-6): integracion y primer release pago

1. Integrar Google Play Billing (PBL 8 recomendado).
2. Implementar entitlement local + backend de validacion de compra.
3. Montar paywall en momentos correctos:
- despues de primer valor real (por ejemplo tras 2-3 bajadas completas), no en primer minuto.
4. Activar experimento A/B de precio.

## Fase 2 (semana 7-12): optimizacion de conversion

1. Iterar onboarding y paywall con datos reales.
2. Activar custom store listings por pais/idioma y creatividades por segmento.
3. Lanzar referral basico:
- 1 mes Pro por invitar rider activo.
4. Preparar MVP de plan coach/club (waitlist + piloto con 2-3 equipos).

---

## 6) Estrategia de crecimiento para sostener monetizacion

Canales prioritarios:
- Comunidades de downhill (clubs, trails, eventos locales).
- Embajadores (riders con audiencia media, no solo mega influencers).
- Alianzas con escuelas/coaches.
- Contenido util: clips de lineas, comparativas de secciones y casos de mejora.

Loop de crecimiento recomendado:
1. Rider graba bajada.
2. Comparte visual de rendimiento/segmentos.
3. Otro rider instala para compararse.
4. Conversion cuando ve utilidad en mejoras por seccion.

---

## 7) Riesgos y como mitigarlos

Riesgo 1: baja conversion inicial  
Mitigacion:
- retrasar paywall hasta primer momento "aha".
- enfatizar valor de progreso y comparacion, no solo almacenamiento.

Riesgo 2: churn alto en mensual  
Mitigacion:
- empujar anual con descuento claro.
- features de retencion: metas semanales, progression cards, resumen mensual.

Riesgo 3: rechazo de Play por politicas de datos/UGC  
Mitigacion:
- disclosure in-app correcto para location background.
- Data safety consistente con comportamiento real.
- en comunidad: ToS, reportar y bloquear usuarios/contenido, moderacion activa.

Riesgo 4: fragilidad tecnica del paywall  
Mitigacion:
- server-side verification de compras + estado de suscripcion.
- fallback offline elegante (no bloquear funciones por error temporal de red).

---

## 8) Requisitos de Play Store que impactan la monetizacion (prioridad alta)

1. Billing para digital goods:
- Si vendes funciones digitales en-app, debes usar Play Billing (con excepciones/regimenes regionales especificos).

2. Service fee:
- 15% en suscripciones auto-renovables.
- 15% en el primer 1M USD para desarrolladores en tier 15%; luego 30% sobre excedente.
- En India/Corea con billing alternativo permitido: reduccion de 4% sobre fee aplicable.

3. Target API:
- Desde 2025-08-31, nuevas apps/updates deben targetear Android 15 / API 35.

4. Account deletion y Data safety:
- Si hay creacion de cuenta en app, debes ofrecer borrado desde app y tambien via enlace web.
- Data safety y politica de privacidad deben reflejar exactamente lo que hace la app.

5. UGC (si mantienes comunidad/chat):
- ToS visible y aceptacion.
- report/block para usuarios y contenido.
- moderacion continua y accion oportuna.

---

## 9) KPIs que debes revisar cada semana

Adquisicion:
- CVR de store listing (visita -> install)
- CAC por canal

Activacion:
- porcentaje que completa primera bajada
- tiempo a primer valor (minutos hasta ver insight util)

Monetizacion:
- paywall view -> trial start
- trial -> paid
- conversion free -> paid a 7/30 dias
- ARPPU y net revenue

Retencion:
- D7, D30
- churn mensual de suscripcion
- porcentaje anual vs mensual

Meta razonable inicial (primer trimestre):
- conversion free->paid: 2-4%
- anualidad dentro de paid: >=60%
- churn mensual: <8-10%

---

## 10) Decisiones recomendadas hoy (orden de ejecucion)

1. Hacer release tecnico para compliance Play:
- target/compile SDK 35
- estabilidad + politicas de datos

2. Implementar monetizacion v1:
- suscripcion mensual/anual + paywall + telemetria de eventos

3. Correr experimento A/B de precio por 4 semanas.

4. Definir piloto B2B (coach/club) con lista de espera.

---

## 11) Fuentes usadas (verificadas)

- Google Play service fees:  
  https://support.google.com/googleplay/android-developer/answer/112622

- Google Play Payments policy (digital goods, alternativas regionales):  
  https://support.google.com/googleplay/android-developer/answer/10281818

- Target API requirements (Android 15 / API 35):  
  https://developer.android.com/google/play/requirements/target-sdk

- Billing Library deprecation timeline (PBL):  
  https://developer.android.com/google/play/billing/deprecation-faq

- User Data policy (privacy, disclosure, account deletion requirement):  
  https://support.google.com/googleplay/android-developer/answer/10144311

- Data safety form requirements:  
  https://support.google.com/googleplay/android-developer/answer/10787469

- App account deletion requirements (detalle operativo):  
  https://support.google.com/googleplay/android-developer/answer/13327111

- UGC moderation requirements (ToS, report/block, moderacion):  
  https://support.google.com/googleplay/android-developer/answer/12923286

- Custom store listings (segmentacion de listing):  
  https://support.google.com/googleplay/android-developer/answer/9156429

- Price experiments en Play Console:  
  https://play.google.com/console/about/price-experiments

- Referencias de mercado (competidores):
  - Strava pricing: https://www.strava.com/pricing
  - Trailforks subscribe: https://www.trailforks.com/subscribe/
  - Komoot pricing: https://support.komoot.com/hc/en-us/articles/360024587532-Komoot-products-and-pricing

---

## 12) Nota final

Este documento es estrategia de producto/comercial y no reemplaza asesoria legal/fiscal local.  
Para lanzamiento en paises especificos, valida impuestos, facturacion y proteccion de datos con asesoria legal en tu jurisdiccion.

