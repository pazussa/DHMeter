# Viabilidad de pago unico para dropIn DH

Fecha: 2026-02-12  
Base analizada: `MONETIZACION_DROPIN_DH.md`

## 1) Pregunta a responder

Si conviene cambiar de modelo principal de `suscripcion` a `pago unico`.

## 2) Supuestos tomados del archivo base

- MAU: `10,000`
- Conversion a pago: `3%`
- Pagos activos estimados: `300`
- Mix suscripcion base: `70% anual / 30% mensual`
- Ingreso bruto mensual estimado (modelo suscripcion): `~1,504 USD`
- Ingreso neto mensual estimado tras fee Play 15%: `~1,278 USD`

## 3) Resultado corto

`Pago unico como modelo principal` es **viable solo a corto plazo** (caja inicial), pero **de alto riesgo a mediano/largo plazo** para una app como dropIn DH que requiere:

- mejoras continuas,
- compatibilidad Android recurrente,
- soporte,
- funciones con costo operativo (cloud/comunidad/moderacion si se expanden).

Conclusión recomendada:
- No migrar a pago unico puro.
- Si quieres ofrecer pago unico, hacerlo como **capa complementaria** dentro de un modelo hibrido.

## 4) Analisis financiero comparativo

## 4.1 Suscripcion (base actual del documento)

- Neto mensual aproximado: `1,278 USD`
- Neto anual aproximado (si se sostiene): `15,336 USD`

Ventaja estructural: ingreso recurrente acumulable.

## 4.2 Pago unico (escenarios)

Formula de neto por compra:
- `Neto por compra = Precio * 0.85` (fee Play 15%)

Compras nuevas requeridas cada mes para igualar `1,278 USD` netos:

| Precio pago unico | Neto por compra | Compras nuevas/mes necesarias |
|---|---:|---:|
| 14.99 USD | 12.74 USD | 101 |
| 19.99 USD | 16.99 USD | 75 |
| 29.99 USD | 25.49 USD | 51 |

Interpretacion:
- Para sostener ingresos, pago unico depende de flujo constante de compradores nuevos.
- Si se frena adquisicion, ingreso cae rapido.
- Suscripcion soporta mejor meses de baja captacion porque conserva base de pago activa.

## 4.3 Efecto en 12 meses (misma cohorte de 300 pagos)

- Suscripcion (segun base): `~15,336 USD netos/año` si el nivel se sostiene.
- Pago unico 19.99 con 300 compras una sola vez:  
  `300 * 19.99 * 0.85 = ~5,097 USD netos` total no recurrente.

Lectura:
- Pago unico puede dar pico inicial.
- Suscripcion genera 3x o mas en valor anual con retencion razonable.

## 5) Viabilidad por tipo de producto

Pago unico puro es mas viable cuando:
- el producto es casi estatico,
- bajo costo de mantenimiento,
- sin servicios recurrentes.

dropIn DH no cae del todo en ese perfil, porque:
- requiere ajustes de sensores y estabilidad por dispositivo,
- depende de cambios de Android/Play,
- tiene roadmap de comunidad/comparaciones avanzadas.

## 6) Riesgos concretos al pasar a pago unico puro

- Riesgo de caja: ingresos volatiles y dependientes de adquisicion mensual.
- Riesgo de sostenibilidad: menor margen para soporte y mejora continua.
- Riesgo de pricing: precio bajo no sostiene negocio; precio alto reduce conversion.
- Riesgo de percepcion: usuarios esperan mejoras constantes en apps deportivas de rendimiento.

## 7) Estrategia recomendada (hibrida)

Modelo sugerido:
- `Free` (adquisicion).
- `Pago unico Core` (lifetime local, sin nube avanzada) para usuarios anti-suscripcion.
- `Pro suscripcion` para funciones recurrentes de alto valor.

Propuesta concreta:
- Pago unico Core: `24.99 - 34.99 USD`
- Suscripcion Pro: mantener banda recomendada del archivo base (`6.99/49.99` o `7.99/59.99`).

Que incluir en pago unico Core:
- analitica local avanzada,
- historial local ilimitado,
- comparaciones basicas/medias.

Que dejar en suscripcion Pro:
- sync/backup cloud,
- features colaborativas avanzadas,
- herramientas de coach/club,
- servicios con costo operativo continuo.

## 8) Como empezar sin riesgo alto

Plan rapido de validacion (4-6 semanas):

1. Mantener suscripcion como principal.
2. Agregar opcion `lifetime core` como experimento.
3. Medir:
- conversion por plan,
- ARPPU,
- canibalizacion de suscripcion,
- retencion y tickets de soporte.
4. Regla de decision:
- Si lifetime canibaliza >30% de pro recurrente sin subir ingreso neto total, reducir alcance del lifetime.
- Si lifetime atrae nuevo segmento sin canibalizacion fuerte, mantener modelo hibrido.

## 9) Decision final recomendada

- **No** cambiar a pago unico como modelo unico.
- **Si** ofrecer pago unico como opcion adicional (hibrido), con limites claros en features recurrentes.

Esta estrategia equilibra:
- preferencia de usuarios que no quieren suscripcion,
- sostenibilidad financiera de largo plazo,
- capacidad de seguir mejorando dropIn DH.

