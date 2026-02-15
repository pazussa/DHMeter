# Analisis de modelo suscripcion con fallback a pago unico (umbral 100)

Fecha: 2026-02-14
Proyecto: dropIn DH (DHMeter)

## 1) Pregunta de negocio

Evaluar si conviene:
1. arrancar con modelo de suscripcion,
2. y si fracasa (menos de 100 suscriptores), pasar a modelo pago unico.

## 2) Base usada para el analisis

Datos internos del repo:
- Precios test suscripcion ya definidos en estrategia:
  - Celda A: 6.99 USD/mes y 49.99 USD/anio.
  - Celda B: 7.99 USD/mes y 59.99 USD/anio.
- Mix pagadores asumido: 70% anual / 30% mensual.
- Fee Play usado para modelar neto: 15%.
- Implementacion actual en app: solo suscripciones (`pro_monthly`, `pro_yearly`) en `app/src/main/java/com/dhmeter/app/monetization/MonetizationCatalog.kt`.

Datos externos verificados (14-feb-2026):
- Regla de fees y pagos de Play.
- Reglas de one-time products y catalogo Play Billing.
- Benchmarks de conversion de suscripciones (RevenueCat).
- Referencias de pricing mercado (Strava, Komoot).

## 3) Numeros clave: que significa llegar o no a 100 suscriptores

Formula de neto mensual por suscriptor:

`Neto por suscriptor = 0.85 * (mix_anual * precio_anual/12 + mix_mensual * precio_mensual)`

Resultado:
- Celda A (6.99 / 49.99): 4.261 USD netos por suscriptor/mes.
- Celda B (7.99 / 59.99): 5.012 USD netos por suscriptor/mes.

Ingreso neto mensual segun base activa:
- 50 suscriptores: 213 a 251 USD/mes.
- 100 suscriptores: 426 a 501 USD/mes.
- 150 suscriptores: 639 a 752 USD/mes.
- 300 suscriptores: 1,278 a 1,504 USD/mes.

Lectura:
- 100 suscriptores NO es "escala"; es un umbral minimo de validacion.
- Con costos operativos bajos (aprox <=500 USD/mes), 100 suscriptores puede ser punto de equilibrio.
- Con costos >500 USD/mes, 100 suscriptores probablemente no alcanza.

## 4) El umbral de 100 es razonable como criterio de "fracaso"?

Si, pero con una condicion de tiempo correcta.

Con benchmarks de conversion download->paid de 1.5% a 2.7%:
- Installs cualificados necesarios para 100 pagos: 3,704 a 6,667.

Si evaluas en 120 dias:
- Requiere aprox 925 a 1,667 installs cualificados por mes.

Conclusion operativa:
- El umbral de 100 tiene sentido si se mide al final de una ventana minima de aprendizaje (recomendado: 120 dias desde lanzamiento publico).
- Medir antes de 90-120 dias puede dar falso negativo.

## 5) Regla de decision recomendada (simple y ejecutable)

Ventana de evaluacion:
- Dia 0 = lanzamiento publico.
- Decision principal en Dia 120.

Regla:
- Si suscriptores activos pagos >= 100 en Dia 120: mantener suscripcion como modelo principal.
- Si suscriptores activos pagos < 100 en Dia 120: activar fallback a pago unico.

Guardarrail para no cortar demasiado pronto:
- Si estas entre 85 y 99 suscriptores pero con crecimiento neto mensual >= 15%, extender 30 dias antes de cambiar.

## 6) Que pasa financieramente al pasar a pago unico

Supuesto de precio pago unico Core:
- 24.99 / 29.99 / 34.99 USD.
- Neto por venta (fee 15%): 21.24 / 25.49 / 29.74 USD.

Ventas one-time por mes para igualar el neto de 100 suscriptores:
- Contra Celda A (426 USD/mes):
  - 24.99 USD: 21 ventas/mes
  - 29.99 USD: 17 ventas/mes
  - 34.99 USD: 15 ventas/mes
- Contra Celda B (501 USD/mes):
  - 24.99 USD: 24 ventas/mes
  - 29.99 USD: 20 ventas/mes
  - 34.99 USD: 17 ventas/mes

Lectura:
- En corto plazo, pago unico puede dar caja rapida.
- En mediano plazo, depende de flujo constante de compradores nuevos.
- Suscripcion gana en valor si un pagador promedio se queda mas de:
  - 5.0-7.0 meses (comparado con one-time de 24.99-34.99 en Celda A)
  - 4.2-5.9 meses (comparado con one-time de 24.99-34.99 en Celda B)

## 7) Riesgo principal de la regla "si <100, switch total a pago unico"

Riesgo:
- Convertir un problema de conversion temprana en un modelo con volatilidad alta de ingresos.

Mitigacion recomendada:
- Hacer fallback a "pago unico como principal" pero dejar una capa Pro por suscripcion para valor recurrente (cloud, comunidad avanzada, coaching).

En practico:
- Modelo que se activa si <100:
  - Free
  - Core Lifetime (principal)
  - Pro subscription (opcional, para valor recurrente)

Esto cumple tu condicion de pasar a pago unico, pero evita cerrar por completo el canal recurrente.

## 8) Factibilidad tecnica del cambio en esta app

Estado actual:
- Billing ya funciona para suscripciones.
- No hay SKU INAPP (one-time) todavia.

Cambios tecnicos minimos para fallback:
1. Agregar SKU `core_lifetime` en catalogo Play y en `MonetizationCatalog.kt`.
2. Extender `BillingManager.kt` para:
   - consultar `ProductType.INAPP`,
   - consultar compras INAPP,
   - fusionar entitlement de suscripcion + lifetime.
3. Actualizar `ProScreen.kt` para mostrar opcion lifetime.
4. Agregar eventos:
   - `purchase_success_lifetime`,
   - `purchase_success_subscription`,
   - `lifetime_to_pro_upgrade`.
5. Definir tabla simple de beneficios (Free vs Core Lifetime vs Pro).

Esfuerzo estimado: bajo a medio (1-2 semanas tecnicas, mas setup Play Console y QA).

## 9) Recomendacion final

Si conviene usar la logica que propones, con esta forma exacta:

1. Lanzar con suscripcion (como esta planeado).
2. Evaluar en Dia 120 con criterio duro de 100 suscriptores activos pagos.
3. Si <100, pasar a modelo pago unico principal (Core Lifetime).
4. Mantener suscripcion Pro como capa opcional para valor recurrente.
5. Re-evaluar 90 dias despues del cambio con estos KPIs:
   - compras one-time/mes,
   - ingreso neto total,
   - canibalizacion de Pro,
   - retencion D30/D90,
   - tickets de soporte por pagador.

Decision esperada:
- Esta estrategia es viable y prudente.
- El umbral de 100 sirve como gate minimo, no como meta final de negocio.

## 10) Fuentes

Internas (repo):
- `MONETIZACION_DROPIN_DH.md`
- `VIABILIDAD_PAGO_UNICO_DROPIN_DH.md`
- `MIGRACION_PAGO_UNICO_A_SUSCRIPCION.md`
- `MONETIZACION_IMPLEMENTACION_ESTADO.md`
- `ESTUDIO_MERCADO_PAGADORES_DROPIN_DH.md`
- `app/src/main/java/com/dhmeter/app/monetization/MonetizationCatalog.kt`
- `app/src/main/java/com/dhmeter/app/monetization/BillingManager.kt`

Externas (consultadas 2026-02-14):
- Google Play service fees:
  https://support.google.com/googleplay/android-developer/answer/112622
- Google Play payments policy:
  https://support.google.com/googleplay/android-developer/answer/10281818
- One-time products (Play Billing):
  https://developer.android.com/google/play/billing/one-time-products
- Manage catalog (Play Billing):
  https://developer.android.com/google/play/billing/manage-catalog
- Target API requirements:
  https://developer.android.com/google/play/requirements/target-sdk
- Strava pricing:
  https://www.strava.com/pricing
- Komoot Premium:
  https://www.komoot.com/premium
- RevenueCat report:
  https://www.revenuecat.com/report
