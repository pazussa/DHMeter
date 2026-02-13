# Configuracion Firebase para Comunidad real

La funcionalidad de comunidad ahora usa backend real en Firebase (Firestore + Auth anonima) para comunicacion entre dispositivos.

## 1) Agregar estas propiedades en `gradle.properties`

```properties
FIREBASE_API_KEY=tu_api_key
FIREBASE_APP_ID=tu_app_id_android
FIREBASE_PROJECT_ID=tu_project_id
FIREBASE_STORAGE_BUCKET=tu_storage_bucket
FIREBASE_MESSAGING_SENDER_ID=tu_sender_id
```

Sin estos valores, la app mostrara error de comunidad en nube no configurada.

## 2) Habilitar servicios en Firebase Console

- Authentication:
  - provider: `Anonymous` habilitado
- Cloud Firestore:
  - crear base en modo produccion o test

## 3) Colecciones que usa la app

- `community_users`
- `community_usernames`
- `community_messages`
- `community_reports`
- subcoleccion por usuario: `community_users/{uid}/blocked_users`

## 4) Reglas sugeridas iniciales (base)

```txt
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /community_users/{userId} {
      allow read: if true;
      allow write: if request.auth != null && request.auth.uid == userId;

      match /blocked_users/{blockedId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }

    match /community_usernames/{usernameKey} {
      allow read: if true;
      allow write: if request.auth != null;
    }

    match /community_messages/{messageId} {
      allow read: if true;
      allow create: if request.auth != null;
      allow update, delete: if false;
    }

    match /community_reports/{reportId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if false;
    }
  }
}
```

## 5) Nota

El control de username unico se hace con transaccion en la coleccion `community_usernames`.
