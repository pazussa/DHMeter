# Publicar en Play sin lanzamiento publico (estado 2026-02-16)

## Resumen rapido
- No es 100% gratis: Google Play Console cobra una cuota unica de registro para la cuenta de desarrollador.
- Si no quieres salir al mercado todavia, usa `Testing > Internal testing`.
- Flujo recomendado: crear clave de subida -> generar `.aab` firmado -> subir a `Internal testing`.

## 1) Generar clave de subida (una sola vez)
Desde la raiz del proyecto:

```powershell
.\scripts\new-upload-key.ps1 -StorePassword "TU_PASSWORD_FUERTE"
```

Esto crea por defecto:
- `.secrets/dhmeter-upload.jks`

Guarda esa clave y password fuera del repo. Sin esa clave no podras actualizar la app en el futuro.

## 2) Generar AAB firmado para Play

```powershell
$env:RELEASE_STORE_FILE=".secrets/dhmeter-upload.jks"
$env:RELEASE_STORE_PASSWORD="TU_PASSWORD_FUERTE"
$env:RELEASE_KEY_ALIAS="upload"
$env:RELEASE_KEY_PASSWORD="TU_PASSWORD_FUERTE"
.\scripts\build-release-aab.ps1
```

Salida esperada:
- `app/build/outputs/bundle/release/app-release.aab`

## 3) Subir sin publicar al mercado
En Play Console:

1. Crea la app (si aun no existe).
2. Ve a `Testing > Internal testing`.
3. Crea release y sube `app-release.aab`.
4. Agrega testers (emails o Google Group).
5. Pulsa `Start rollout to internal testing`.

Con esto solo testers invitados pueden instalarla; no queda publica en produccion.

## 4) Nota para produccion futura
Si tu cuenta es `personal` y app creada despues del 13 de noviembre de 2023, para pasar a produccion normalmente necesitas completar prueba cerrada con al menos 12 testers durante 14 dias.
