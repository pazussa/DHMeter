# DHMeter

DH Meter es una aplicación para medir y comparar impactos y métricas de recorridos (runs) en bici.

## Qué hace
- Detecta impactos y calcula un `impactScore` acumulado por run.
- Permite comparar múltiples runs (N) a la vez con una vista de métricas y veredicto.
- Genera gráficos por run y permite exportar/apk para pruebas.

## Construcción
Requiere Java/Android SDK y Gradle.

Para compilar (desde el directorio del proyecto):

```bash
./gradlew :app:assembleDebug
```

El APK generado estará en `app/build/outputs/apk/debug/app-debug.apk`.

## Notas rápidas
- Umbral de impacto ajustado a 0.8g en la versión actual.
- Comparación multi-run real implementada; la UI soporta muchos runs con scroll horizontal.

## Contacto
pazussa

