// ─────────────────────────────────────────────────────────────
//  Configuración del video promocional de Marea
//  (todo lo que se ajusta para sincronizar con la voz está aquí)
// ─────────────────────────────────────────────────────────────

export const FPS = 30;

export type Caption = [atSeconds: number, text: string];
export type SceneDef = {id: string; seconds: number; captions: Caption[]};

/**
 * Las 6 escenas, una por bloque del guion.
 * - seconds: cuánto dura la escena.
 * - captions: frases de subtítulo y el segundo (desde el inicio de la
 *   escena) en que aparecen. Las animaciones internas (beneficios de
 *   Premium, tarjetas de planes, tu top, letras…) se disparan con estos
 *   mismos tiempos, así que al alinear los subtítulos con la voz,
 *   todo el video queda sincronizado.
 */
export const SCENES: SceneDef[] = [
  {
    id: 'intro',
    seconds: 6.6,
    captions: [
      [0.0, '¿Y si tu música no tuviera límites?'],
      [2.39, 'Llega Marea, la app donde suena todo lo que amas, a tu manera.'],
    ],
  },
  {
    id: 'premium',
    seconds: 20.35,
    captions: [
      [0.26, 'Empieza gratis: escucha lo que quieras con anuncios,'],
      [3.11, 'y cuando estés listo, pásate a Premium.'],
      [5.51, 'Sin anuncios que te interrumpan.'],
      [7.35, 'Con saltos ilimitados para pasar de una canción a otra sin esperar.'],
      [11.15, 'Descargas para llevarte tu música a todas partes, incluso sin internet.'],
      [15.41, 'Y un ecualizador profesional, para que cada tema suene exactamente como tú quieres.'],
    ],
  },
  {
    id: 'planes',
    seconds: 7.2,
    captions: [
      [0.24, 'Elige el plan que va contigo:'],
      [1.89, 'Individual, Dúo o Familiar,'],
      [4.14, 'y comparte la experiencia con las personas que más quieres.'],
    ],
  },
  {
    id: 'descubre',
    seconds: 9.6,
    captions: [
      [0.2, 'Marea aprende de ti.'],
      [1.87, 'Descubre tu top,'],
      [2.97, 'recibe recomendaciones de tus artistas favoritos'],
      [5.72, 'y canta con las letras sincronizadas en pantalla, palabra por palabra.'],
    ],
  },
  {
    id: 'diseno',
    seconds: 4.3,
    captions: [[0.22, 'Todo esto, con un diseño elegante y fluido, hecho para disfrutarse.']],
  },
  {
    id: 'cta',
    seconds: 8.2,
    captions: [
      [0.21, 'Marea.'],
      [1.01, 'Tu música, sin límites.'],
      [2.83, 'Descárgala ahora y siente la diferencia.'],
    ],
  },
];

/**
 * VOZ DE ELEVENLABS
 * Copia el MP3 en promo/public/voz/ y pon aquí su nombre, p. ej. 'voz/marea.mp3'.
 * Empieza en el segundo VOICE_START del video. Los tiempos de SCENES
 * están medidos sobre narracion-marea.mp3 (pausas detectadas con ffmpeg silencedetect).
 */
export const VOICE_FILE: string | null = 'voz/marea.mp3';
export const VOICE_START = 0;
export const VOICE_VOLUME = 1;

/** Música de fondo opcional (p. ej. 'musica.mp3' en public/). Baja sola si hay voz. */
export const MUSIC_FILE: string | null = null;
export const MUSIC_VOLUME = 0.35;
export const MUSIC_VOLUME_UNDER_VOICE = 0.12;

/** Subtítulos (útiles en redes, donde el video arranca en silencio). */
export const SHOW_CAPTIONS = true;

/** Frames que se cruzan dos escenas en la transición. */
export const TRANSITION = 16;

export const COLORS = {
  bg: '#0b1720',
  bgDeep: '#050d15',
  accent: '#1e90f0',
  accent2: '#3aa8ff',
  cyan: '#2fd4e6',
  text: '#eef6fb',
  muted: '#93a9bb',
  glass: 'rgba(255,255,255,0.055)',
  glassBorder: 'rgba(255,255,255,0.12)',
};

export const FONT_TITLE = "'Bricolage Grotesque', system-ui, sans-serif";
export const FONT_TEXT = "'Figtree', system-ui, sans-serif";

export const PLANS = [
  {name: 'Individual', price: 50, seats: 1, note: '1 cuenta'},
  {name: 'Dúo', price: 80, seats: 2, note: '2 cuentas'},
  {name: 'Familiar', price: 180, seats: 6, note: 'Hasta 6 cuentas'},
];

export const DOMAIN = 'marea.ventasthalia.com';

// ── utilidades derivadas ─────────────────────────────────────
export const sceneFrames = (s: SceneDef) => Math.round(s.seconds * FPS);
export const sceneStarts = () => {
  const starts: number[] = [];
  let acc = 0;
  for (const s of SCENES) {
    starts.push(acc);
    acc += sceneFrames(s);
  }
  return {starts, total: acc};
};
/** Frame (relativo a la escena) en que arranca el subtítulo/beat i. */
export const beat = (s: SceneDef, i: number) => Math.round(s.captions[i][0] * FPS);
