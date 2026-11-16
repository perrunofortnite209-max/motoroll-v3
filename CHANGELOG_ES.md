# Cambios de Motoroll Home 3.0

- Rehecha la pantalla principal para que se comporte como un launcher, no como un panel de accesos rápidos.
- Eliminados los tiles dedicados a Cámara y Modo Gaming.
- Añadido gesto de deslizar hacia abajo para abrir el cajón y hacia arriba para cerrarlo.
- Cajón propio con buscador y cuadrícula de apps.
- Añadido selector de imagen para el fondo del launcher con persistencia de URI.
- Fondo de reserva estático negro/azul/violeta sin logotipo.
- Carga de wallpaper con muestreo y RGB_565 para limitar memoria.
- Manejo de fallos de inicio y logging para diagnóstico.
- Mantiene el package id y aumenta versionCode a 3.

## 3.0.1
- Corregido error de compilación: `getHomeApps()` no existía.
- `onResume()` vuelve a utilizar `favoriteApps()` con el mismo estilo de iconos de la pantalla principal.
- Aumentado `versionCode` a 4 para permitir actualizar la instalación 3.0.
