# Estado de implementacion de monetizacion (sin reglas comerciales)

Referencia usada: `MONETIZACION_DROPIN_DH.md`  
Fecha: 2026-02-13

## Alcance aplicado

Se implemento todo lo posible en codigo para dejar la app lista para monetizar, **excepto reglas de monetizacion** (pricing final, timing de paywall, gating de features), tal como pediste.

## Implementado en la app

### 1) Google Play Billing integrado

- Dependencia agregada en `app/build.gradle.kts`:
  - `com.android.billingclient:billing-ktx:7.1.1`
- Catalogo de productos:
  - `pro_monthly`
  - `pro_yearly`
- Infra de billing:
  - `app/src/main/java/com/dhmeter/app/monetization/BillingManager.kt`
  - carga de productos
  - restaurar compras
  - flujo de compra de suscripcion
  - persistencia local de entitlement Pro

### 2) Entitlement local + base para validacion backend

- Interfaz de validacion agregada:
  - `app/src/main/java/com/dhmeter/app/monetization/PurchaseValidator.kt`
- Implementacion local inicial:
  - `LocalPurchaseValidator`
- Binding DI:
  - `app/src/main/java/com/dhmeter/app/di/AppModule.kt`

Esto deja listo el punto de migrar a validacion server-side sin reestructurar UI ni billing flow.

### 3) Paywall/Pro screen operativo

- Nueva pantalla Pro:
  - `app/src/main/java/com/dhmeter/app/ui/screens/pro/ProScreen.kt`
  - `app/src/main/java/com/dhmeter/app/ui/screens/pro/ProViewModel.kt`
- Navegacion agregada:
  - `app/src/main/java/com/dhmeter/app/ui/navigation/Navigation.kt`
- Acceso desde Home:
  - `app/src/main/java/com/dhmeter/app/ui/screens/home/HomeScreen.kt`

### 4) Medicion de eventos de monetizacion

- Tracker local agregado:
  - `app/src/main/java/com/dhmeter/app/monetization/EventTracker.kt`
  - `app/src/main/java/com/dhmeter/app/monetization/MonetizationCatalog.kt`
- Eventos ya instrumentados:
  - `install`
  - `onboarding_complete`
  - `first_run_complete`
  - `paywall_view`
  - `purchase_success`
  - `trial_start`
  - `churn`

Hooks aplicados en:
- `app/src/main/java/com/dhmeter/app/DHMeterApplication.kt`
- `app/src/main/java/com/dhmeter/app/ui/screens/home/HomeViewModel.kt`
- `app/src/main/java/com/dhmeter/app/ui/screens/recording/RecordingViewModel.kt`
- `BillingManager`

### 5) UGC compliance para comunidad (Play)

Implementado en comunidad:
- Aceptacion de terminos antes de registro
- Reportar mensaje
- Reportar usuario
- Bloquear usuario
- Desbloquear usuario
- Persistencia local de reportes y bloqueos

Archivos:
- `app/src/main/java/com/dhmeter/app/community/CommunityModels.kt`
- `app/src/main/java/com/dhmeter/app/community/CommunityRepository.kt`
- `app/src/main/java/com/dhmeter/app/ui/screens/community/CommunityViewModel.kt`
- `app/src/main/java/com/dhmeter/app/ui/screens/community/CommunityScreen.kt`

### 6) Account deletion in-app

- Agregado flujo de borrar cuenta local de comunidad desde UI
- Elimina usuario local actual y sus mensajes
- Se registran eventos de auditoria:
  - `account_deletion_started`
  - `account_deletion_completed`

## Ajustes legales/UX de disclosure aplicados

- Ayuda de Home actualizada para explicar:
  - uso de sensores + ubicacion
  - uso en segundo plano durante grabacion
  - ruta para eliminar cuenta en app
  - contacto: `dropindh@gmail.com`

Archivo:
- `app/src/main/java/com/dhmeter/app/ui/screens/home/HomeScreen.kt`

## Pendiente (manual / externo al codigo)

Estos puntos no se pueden cerrar solo con cambios locales de codigo:

1. Configurar productos reales en Play Console (`pro_monthly`, `pro_yearly`) y ofertas.
2. Completar formularios de Data Safety en Play Console con datos reales.
3. Publicar URL web de privacidad y URL web de borrado de cuenta para ficha Play.
4. Implementar verificacion server-side real (token validation backend).
5. Definir reglas de monetizacion (pricing final, trial, paywall timing, gating), que se excluyeron por alcance.

## Verificacion tecnica ejecutada

- `./gradlew :app:assembleDebug` ✅
- `./gradlew :app:assembleRelease` ✅
- `./gradlew :app:testDebugUnitTest` ✅

APK release generado en:
- `app/build/outputs/apk/release/app-release.apk`
