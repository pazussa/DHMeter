# Estudio de mercado: cuantos podrian pagar por dropIn DH

Fecha del analisis: **2026-02-13**

## 1) Respuesta corta

- **Pool potencial total de pagadores (largo plazo): 39,000 a 397,000 usuarios**
- **Estimacion base para planear: 141,600 usuarios**
- **Pool capturable en 24 meses (ejecucion realista): 1,560 a 62,400**
- **Meta operativa recomendada a 24 meses: 5,000 a 15,000 pagadores**

## 2) Objetivo

Estimar cuantas personas **podrian pagar** por `dropIn DH` (telemetria downhill / gravity) usando datos de mercado y benchmarks de conversion.

## 3) Datos base usados

1. Strava reporta mas de **180M usuarios** en **185+ paises** (dic 2025).
2. En EE. UU. hay aprox **8.7M mountain bikers** (6+) segun TPL (dato de 2021 citado en reporte 2025).
3. En mountain/gravel, **41.5%** son participantes core (13+ salidas por ano), citado por TPL desde OIA.
4. Cuota Android (ene 2026):
   - Mundial: **70.36%**
   - Europa: **60.48%**
   - Sudamerica: **81.54%**
   - EE. UU. (mobile): **39.95%**
5. Benchmarks de pago (RevenueCat):
   - 2024: **1.7%** download->paid en 30 dias (promedio global)
   - 2025 D35 mediano: **1.5%** (apps de precio bajo), **2.7%** (apps de precio alto)
   - North America upper quartile: **5.5%**

## 4) Metodologia

Modelo por capas:

`Pagadores = Audiencia base x Ajuste disciplina x Ajuste Android x Tasa de pago`

Nota: donde no hay dato publico exacto para downhill, se usa un rango inferido y se marca como supuesto.

## 5) Escenario A (ancla EE. UU.)

- Base: 8.7M MTB riders
- Android EE. UU. 39.95% -> **3.48M**
- Core riders 41.5% -> **1.44M**
- Supuesto: fraccion gravity/downhill fit = **20%-35%** -> **288k a 504k**
- Supuesto: disposicion a pagar = **4%-12%**
- Resultado: **11.5k a 60.5k pagadores potenciales** solo en EE. UU.

Este escenario valida que, aun en nicho, hay mercado pagador suficiente.

## 6) Escenario B (pool potencial global para dropIn DH)

Partiendo de 180M usuarios Strava:

1. Supuesto: usuarios con uso real de ciclismo = **15%-30%**
2. Supuesto: dentro de ciclismo, segmento MTB/gravity fit = **8%-15%**
3. Supuesto: mix Android para mercados target = **60%-70%**

Resultado intermedio:

- Audiencia Android gravity fit: **1.30M a 5.67M**
- Caso base (punto medio): **2.83M**

Supuesto de pago sobre audiencia fit:

- Conservador: **3%**
- Base: **5%**
- Alto: **7%**

Resultado de pagadores potenciales:

- Conservador: **38,900**
- Base: **141,600**
- Alto: **396,900**

## 7) Cuantos podrian pagar (estimacion final)

- **Respuesta recomendada:** entre **39k y 397k**, con base en **~142k**.
- Interpretacion: el techo de mercado es de **decenas de miles a cientos de miles** si el producto y la distribucion se ejecutan bien.

## 8) Cuantos puedes capturar en 24 meses

Supuestos de ejecucion:

- Penetracion de audiencia fit: 8% / 12% / 20%
- Conversion download->paid: 1.5% / 2.7% / 5.5%

Resultados:

- Conservador: `1.30M x 8% x 1.5%` = **1,560**
- Base: `2.83M x 12% x 2.7%` = **9,200**
- Alto: `5.67M x 20% x 5.5%` = **62,400**

Lectura operativa:

- Objetivo sano para presupuesto y equipo pequeno: **5k-15k pagadores** en 24 meses.

## 9) Regla rapida por instalaciones (para seguimiento mensual)

Si tomas conversion realista inicial de **1.5%-2.7%**:

- 50,000 installs cualificados -> **750 a 1,350** pagadores
- 100,000 installs cualificados -> **1,500 a 2,700** pagadores
- 250,000 installs cualificados -> **3,750 a 6,750** pagadores

Si ejecutas muy bien (5.5%):

- 100,000 installs cualificados -> **5,500** pagadores

## 10) Limites del analisis

- Alta confianza: Android share, base Strava, benchmarks de conversion.
- Media-baja confianza: proporcion exacta downhill/gravity dispuesta a pagar (no hay serie publica unica).
- Por eso se entrega **rango**, no numero unico fijo.

## 11) Fuentes

1. Strava Press - Year in Sport 2025:
   https://press.strava.com/en-gb/articles/strava-releases-12th-annual-year-in-sport-trend-report-2025
2. Trust for Public Land - Economic Benefits of Mountain Biking (PDF):
   https://www.tpl.org/wp-content/uploads/2025/04/040225_Green-Paper_Mountain-Biking_FINAL3.pdf
3. Statcounter - Mobile OS share worldwide:
   https://gs.statcounter.com/os-market-share/mobile/worldwide
4. Statcounter - Mobile OS share Europe:
   https://gs.statcounter.com/os-market-share/mobile/europe
5. Statcounter - Mobile OS share South America:
   https://gs.statcounter.com/os-market-share/mobile/south-america
6. Statcounter - Mobile OS share United States:
   https://gs.statcounter.com/os-market-share/mobile/united-states-of-america
7. RevenueCat - State of Subscription Apps 2024:
   https://www.revenuecat.com/state-of-subscription-apps-2024/
8. RevenueCat - State of Subscription Apps 2025:
   https://www.revenuecat.com/report
