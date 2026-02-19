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

## Publicación automática de APK (recomendado para pruebas)
La mejor opción gratuita para tu caso es **GitHub Releases + GitHub Actions**.

Ventajas:
- Gratis.
- Tienes un link único de descarga desde Android.
- Se actualiza automáticamente en cada push a `main` o ejecución manual del workflow.

Workflow incluido:
- `.github/workflows/publish-testing-apk.yml`
- Publica/actualiza una release con tag `testing-latest`.
- Asset fijo para descargar siempre desde el mismo enlace:
`https://github.com/<usuario>/<repo>/releases/download/testing-latest/dropindh-testing-latest.apk`

Pasos para activarlo:
1. Sube este repo a GitHub (idealmente público para descargar sin login).
2. En GitHub, ve a `Settings > Secrets and variables > Actions`.
3. Opcional para build release firmada (recomendado para poder actualizar sin desinstalar):
- `RELEASE_STORE_FILE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
4. Opcional: `MAPS_API_KEY` si quieres la key de mapas en CI.
5. Ejecuta el workflow `Publish Testing APK` (manual) o haz push a `main`.

Notas:
- Si no configuras secrets de firma, el workflow publica `debug` (válido para pruebas rápidas).
- Para instalar desde Android, habilita "instalar apps desconocidas" para tu navegador/gestor.

## Notas rápidas
- Umbral de impacto ajustado a 0.8g en la versión actual.
- Comparación multi-run real implementada; la UI soporta muchos runs con scroll horizontal.

## Contacto
pazussa
