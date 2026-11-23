# Video promocional de Marea (Remotion)

Video de ~56 s, 1920×1080, 30 fps, sincronizado con la narración de ElevenLabs (`public/voz/marea.mp3`).

```bash
npm install
npm run studio     # vista previa interactiva
npm run render     # genera out/marea-promo.mp4
```

Si Chrome no se descarga solo, apunta a un Chromium local: `REMOTION_BROWSER=/ruta/a/chrome npm run render`.

## Ajustes (todo en `src/config.ts`)
- **Voz**: `VOICE_FILE`. Si cambias la narración, ajusta `SCENES[].seconds` y los tiempos de `captions` (segundos desde el inicio de cada escena). Las animaciones internas se disparan con esos mismos tiempos. Para ver las pausas: `ffmpeg -i public/voz/marea.mp3 -af silencedetect=noise=-30dB:d=0.12 -f null -`.
- **Música de fondo**: pon el archivo en `public/` y asigna `MUSIC_FILE`; baja sola bajo la voz.
- **Subtítulos**: `SHOW_CAPTIONS`.
- **Precios / dominio**: `PLANS`, `DOMAIN`.
