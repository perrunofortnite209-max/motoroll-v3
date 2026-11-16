# Actualizar tu repositorio existente

Puedes reemplazar el contenido de tu repositorio por el proyecto MotorollHome_v3 completo. Si ya tienes un workflow que te funcionó, conserva `.github/workflows/build-apk.yml` o usa el incluido en el proyecto.

La compilación usa JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.0, Android SDK 36 y crea `app/build/outputs/apk/debug/app-debug.apk`.

El nuevo APK usa el mismo `applicationId` (`com.motoroll.home`) y `versionCode=3`, por lo que se instala como actualización con:

```cmd
adb install -r app-debug.apk
```

Tras instalar, elige Motoroll Home como app de inicio predeterminada.
