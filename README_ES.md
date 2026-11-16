# Motoroll Home 3.0

Launcher Android independiente para Motoroll OS, pensado para Android 16 y pantallas de alta densidad.

## Cambios principales

- Escritorio de launcher con reloj, fecha, favoritos y dock.
- Cajón de aplicaciones propio: se abre deslizando **hacia abajo** en el escritorio y se cierra deslizando **hacia arriba** o con Atrás.
- Cajón con buscador, cuadrícula de cuatro columnas y orden alfabético.
- Botón **FONDO** para elegir una imagen desde el selector de documentos de Android. La selección se guarda para siguientes aperturas.
- Si no se elige imagen, utiliza un fondo de color negro con brillos estáticos azul y violeta; no incluye un wallpaper con logotipo.
- No incluye accesos rápidos fijos a Cámara ni a Modo Gaming.
- No usa servicios en segundo plano, red, analítica, fondos animados, blur pesado ni bibliotecas UI externas.
- Mantiene el paquete `com.motoroll.home` para actualizar instalaciones previas con `adb install -r`.

## Compilar con GitHub Actions

1. Sube el contenido de esta carpeta a la raíz del repositorio (incluida `.github/workflows/build-apk.yml`).
2. Abre **Actions** y ejecuta **Build Motoroll Home APK** o haz un push a `main`.
3. Descarga el artefacto `MotorollHome-3.0-debug-apk`.
4. Dentro del artefacto estará `app-debug.apk`.

## Instalar o actualizar

```cmd
adb install -r app-debug.apk
```

Después selecciona Motoroll Home en Ajustes → Aplicaciones → Aplicaciones predeterminadas → Aplicación de inicio.

## Cambiar el fondo del launcher

En la pantalla principal pulsa **FONDO**, elige una imagen en el selector de Android y vuelve al escritorio. La imagen se aplica solamente dentro de Motoroll Home, no cambia el fondo de bloqueo del sistema.

## Gestos

- Deslizar hacia abajo en el escritorio: abrir cajón de aplicaciones.
- Deslizar hacia arriba en el cajón: volver al escritorio.
- Botón Atrás: cerrar cajón.

## Diagnóstico si se cierra

La app muestra y registra errores de inicio. Para recopilar el registro tras reproducir un cierre:

```cmd
adb logcat -c
adb shell monkey -p com.motoroll.home 1
adb logcat -d -s AndroidRuntime:E MotorollHome:E
```

Comparte la salida si todavía se cierra. El código fuente se entrega para compilación; no se ha probado en el dispositivo físico desde este entorno.


### Corrección 3.0.1
Esta revisión corrige la referencia a `getHomeApps()` que detenía la compilación Java. La actualización de la lista de inicio usa `favoriteApps()`, igual que la primera construcción de la pantalla principal. El `versionCode` pasa a 4 y `versionName` a 3.0.1.
