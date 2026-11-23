# Marea

Reproductor de audio y gestor de biblioteca musical open-source para Android, construido en **Kotlin + Jetpack Compose** con **Jetpack Media3**.

## Descarga (APK)

Descarga la última versión precompilada desde la sección de [Releases](https://github.com/1xdxsd-cmd/marea/releases).
También puedes recibir actualizaciones automáticas en Android usando **Obtainium** con la URL de este repositorio.

## Características principales

- **Reproductor completo:** reproducción en segundo plano (`MediaSessionService`), caché de streaming HTTP/2 (`OkHttpDataSource` + `SimpleCache` de 150 MB), modos secuencial, aleatorio y repetición (`OFF`, `ALL`, `ONE`), más letras vía LRCLIB.
- **Descarga 1-Click y playlists (Premium):** descarga paralela por rangos HTTP en formato `.m4a` con cola en segundo plano (`WorkManager` + `Room`), cancelación/eliminación en cola e importación por enlace compatible (incluyendo *Compartir → Marea*).
- **Portadas HD y validación PSNR:** enriquecimiento automático mediante APIs abiertas (iTunes Search, Deezer, MusicBrainz / Cover Art Archive) con filtro de calidad **PSNR >= 20 dB**, escritas en el archivo con `JAudiotagger`.
- **Diseño cristal:** superficies translúcidas, portada difuminada de fondo en el reproductor, brillos azules y letra sincronizada.
- **Playlists y vista por artista:** creación/gestión de playlists locales, filtro de canciones originales en búsqueda, vista estricta de artista (incluyendo colaboraciones explícitas) y recomendaciones personalizadas basadas en tu biblioteca (ListenBrainz Labs + Deezer + grafo de canciones relacionadas).

## Stack Técnico

- **UI:** Kotlin, Jetpack Compose, Material 3, Coil (caché `RGB_565`)
- **Audio & Media:** Jetpack Media3 (`ExoPlayer`, `MediaSession`, `Transformer`, `OkHttpDataSource`, `SimpleCache`)
- **Extracción & Etiquetado:** `NewPipeExtractor`, `JAudiotagger`, `OkHttp`
- **Persistencia & Tareas:** `Room`, `WorkManager`, Coroutines & Flow

## Compilación desde el código fuente

Requisitos: JDK 17+ y Android SDK (`compileSdk = 36`, `minSdk = 24`).

```bash
./gradlew testDebugUnitTest assembleDebug
```

Para generar APK `release` firmado, define las variables de entorno `MAREA_KEYSTORE_PATH`, `MAREA_KEYSTORE_PASS`, `MAREA_KEY_ALIAS` y `MAREA_KEY_PASS`, y ejecuta:

```bash
./gradlew assembleRelease
```

## Licencia y Créditos

Este proyecto está licenciado bajo **GNU General Public License v3.0 (GPL-3.0)**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

Marea parte de un proyecto open-source de [jsguaman](https://github.com/jsguaman), también bajo GPL-3.0.

Librerías y APIs open-source/abiertas utilizadas:
- `NewPipeExtractor` (GPL-3.0)
- `JAudiotagger` (LGPL)
- `Jetpack Media3`, `Room`, `WorkManager`, `Jetpack Compose` (Apache-2.0)
- `OkHttp`, `Coil` (Apache-2.0)
- `LRCLIB`, `MusicBrainz / ListenBrainz Labs`, `iTunes Search API`, `Deezer API`

## Aviso

Marea es un cliente reproductor y gestor de audio open-source. No aloja ni distribuye contenido multimedia protegido por derechos de autor. Cada usuario es responsable del uso de la aplicación y del cumplimiento de las normativas y términos de servicio aplicables.
