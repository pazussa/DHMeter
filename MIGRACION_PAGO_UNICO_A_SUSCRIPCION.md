# Migrar de pago unico a suscripcion en dropIn DH

Fecha: 2026-02-12  
Base de analisis: `MONETIZACION_DROPIN_DH.md` + `VIABILIDAD_PAGO_UNICO_DROPIN_DH.md`

## Resumen ejecutivo

Si, **es viable empezar con pago unico y luego migrar a suscripcion**, pero solo bajo una migracion tipo "soft":

- Mantener (grandfather) lo comprado en pago unico.
- Introducir suscripcion para valor nuevo/recurrente.
- Dejar de vender pago unico a nuevos usuarios cuando convenga.

No es viable (ni recomendable) una migracion tipo "hard" donde:
- se intenta convertir automaticamente compras de pago unico en cobro recurrente, o
- se retira acceso a features que se vendieron como perpetuas.

## Conclusion corta

- Viable: `SI`, con modelo hibrido y reglas claras.
- Viable como "cambio forzoso" a suscripcion: `NO`.

## 1) Lo que se puede y no se puede hacer (Play Billing + producto)

## Se puede

1. Vender one-time product (no consumible) y tambien suscripciones en la misma app.
2. Dejar de vender el one-time a nuevos usuarios mas adelante (deprecacion/catalogo).
3. Mantener derechos de usuarios legacy y ofrecerles suscripcion opcional con oferta.
4. Usar ofertas con elegibilidad "developer determined" para segmentar migracion.

## No se puede (de forma nativa/automatica)

1. Convertir automaticamente una compra one-time en cobro recurrente.
2. Cobrar suscripcion sin accion explicita de compra del usuario.
3. Tratar el one-time como si fuera renovable (tecnicamente no lo es).

Inferencia desde fuentes: Google define el one-time no consumible como compra unica con beneficio permanente asociado a cuenta; la suscripcion es un producto distinto con renovacion, por eso no existe "conversion automatica" directa entre ambos modelos.

## 2) Viabilidad comercial real para dropIn DH

Con los supuestos del archivo base (10,000 MAU, 3% paid), la suscripcion soporta mejor:
- mantenimiento continuo,
- soporte de dispositivos,
- evolucion de features,
- costos cloud/moderacion futura.

Pago unico sirve para:
- reducir friccion inicial,
- captar usuarios anti-suscripcion,
- generar caja temprana.

Riesgo principal:
- si te quedas solo en pago unico, dependes de compradores nuevos cada mes.

Por eso, la estrategia viable es:
- arrancar con pago unico solo si ya dejas preparado el camino a suscripcion.

## 3) Arquitectura recomendada desde el dia 1 (para migrar sin dolor)

Define entitlements por capas, no por SKU suelto:

- `core_perpetual` -> desbloqueado por one-time (sin vencimiento).
- `pro_recurring` -> desbloqueado por suscripcion (con `expiresAt`).

Modelo de datos recomendado:
- `user_id`
- `source_product_id`
- `source_type` (`one_time` | `subscription`)
- `entitlement_code`
- `granted_at`
- `expires_at` (null para perpetuo)
- `status` (`active`, `revoked`, `expired`)

En la app:
- consultar compras `INAPP` y `SUBS`,
- fusionar estado local + backend,
- nunca perder acceso legacy validamente comprado.

## 4) Plan de migracion recomendado (practico)

## Fase A: Lanzamiento inicial (pago unico)

Producto:
- `otp_core_lifetime`

Mensaje:
- "Pago unico para funciones Core actuales"

Guardarrailes:
- Evita prometer "todo futuro para siempre" en descripcion comercial.

## Fase B: Introduccion de suscripcion (6-12 semanas despues)

Productos:
- `pro_monthly`
- `pro_yearly`

Mover a suscripcion solo features de costo recurrente:
- sync cloud,
- comparativas avanzadas en nube,
- comunidad avanzada,
- herramientas coach/club.

## Fase C: Migracion comercial

1. Legacy users conservan `core_perpetual`.
2. Se ofrece upgrade opcional a Pro con beneficio leal:
- descuento temporal,
- trial extendido,
- oferta tagged para usuarios elegibles.
3. Se corta venta de `otp_core_lifetime` para nuevos usuarios (si metricas lo justifican).

## Fase D: Estado estable

- Modelo principal: suscripcion.
- One-time opcional solo si agrega captura incremental sin canibalizar.

## 5) Reglas de decision (go/no-go)

Pasa de pago unico-only a hibrido/sub cuando se cumplan 2 de 3:

1. Nuevas compras one-time/mes no cubren costos fijos + roadmap.
2. Retencion y uso muestran valor recurrente claro (uso semanal + historial).
3. Proyeccion de MRR de suscripcion supera ingreso de one-time con menor volatilidad.

## 6) Riesgos de migrar y mitigacion

Riesgo: backlash de usuarios legacy  
Mitigacion:
- mantener derechos comprados,
- comunicar "nuevo valor en Pro", no "te quitamos lo que ya pagaste".

Riesgo: canibalizacion (nadie se suscribe porque todos compran lifetime)  
Mitigacion:
- limitar alcance de lifetime al Core,
- ubicar features recurrentes en Pro.

Riesgo: complejidad tecnica de estados de compra  
Mitigacion:
- backend de entitlements + RTDN + reconciliacion periodica.

Riesgo: confusion comercial  
Mitigacion:
- tabla simple in-app: Core Lifetime vs Pro.

## 7) Recomendacion final para dropIn DH

Si quieres empezar con pago unico, hazlo asi:

1. Lanza `Core Lifetime` (precio unico).
2. Diseña desde ya entitlements para coexistencia con suscripcion.
3. En 2-3 ciclos de producto, introduce `Pro` suscripcion con valor realmente nuevo.
4. Mantiene legacy para quien compro one-time.
5. Evalua cortar venta de lifetime cuando el Pro ya convierta de forma estable.

Esto te da:
- velocidad de lanzamiento comercial,
- menor rechazo inicial,
- camino sostenible a ingresos recurrentes.

## 8) Fuentes oficiales usadas

- One-time products (definicion y tipos):  
  https://developer.android.com/google/play/billing/one-time-products

- One-time purchase lifecycle (ack, refund/revoke, estados):  
  https://developer.android.com/google/play/billing/lifecycle/one-time

- Manage catalog / product deprecation:  
  https://developer.android.com/google/play/billing/manage-catalog

- Catalog and offer eligibility (Play Console Help):  
  https://support.google.com/googleplay/android-developer/answer/16431770

- Subscriptions and offer eligibility examples (developer determined):  
  https://support.google.com/googleplay/android-developer/answer/12154973

