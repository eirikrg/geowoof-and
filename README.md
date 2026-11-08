# GeoWoof · Android

App de GeoWoof, una red social para dueños de mascotas: paseos con GPS, paseantes en directo, seguidores, publicaciones y chat. El backend está en [geowoof-backend](https://github.com/eirikrg/geowoof-backend).

Kotlin, Jetpack Compose y Material 3, con Hilt, Room, WorkManager y Paging 3.

## Arrancar

1. Arranca el backend con `docker compose up --build` en `geowoof-backend`.
2. Abre `android/` en Android Studio, o compila desde la terminal:

   ```bash
   cd android
   ./gradlew assembleDebug
   ```

Por defecto, la app se conecta a `http://10.0.2.2:8080/`, que es tu ordenador visto desde el emulador. Las claves son opcionales: la de Google Maps y la del inicio de sesión con Google van en `android/secrets.properties` (ver `android/secrets.properties.example`), y para las notificaciones push hace falta el `android/app/google-services.json` de Firebase.

## Tests

```bash
cd android
./gradlew testDebugUnitTest
```
