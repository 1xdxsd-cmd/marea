import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import {COLORS, FONT_TEXT, FONT_TITLE} from './config';
import {Cover, EqBars, IconNext, IconPause, IconPrev, IconSkip, Logo, clamp} from './ui';

// Pantallas de la app dentro del teléfono (ancho base 396 px).

export const TRACKS = [
  {t: 'Olas de medianoche', a: 'Tu mix de Marea'},
  {t: 'Neón en la costa', a: 'Lía Montero'},
  {t: 'Brisa de enero', a: 'Los Faros'},
  {t: 'Corazón eléctrico', a: 'Dani Vega'},
  {t: 'Sal y arena', a: 'Mar Abierto'},
  {t: 'Luces del puerto', a: 'Nube Azul'},
];

const font = (size: number, weight = 500, color: string = COLORS.text): React.CSSProperties => ({
  fontFamily: FONT_TEXT,
  fontSize: size,
  fontWeight: weight,
  color,
});

export const PlayerScreen: React.FC<{
  track?: number;
  progress?: number;
  premium?: boolean;
  overlay?: React.ReactNode;
  pulseNext?: number;
}> = ({track = 0, progress = 0.3, premium = true, overlay, pulseNext = 0}) => {
  const frame = useCurrentFrame();
  const tr = TRACKS[track % TRACKS.length];
  const breathe = 1 + Math.sin(frame / 18) * 0.012;
  return (
    <div style={{position: 'absolute', inset: 0}}>
      {/* portada difuminada de fondo */}
      <div style={{position: 'absolute', inset: -80, filter: 'blur(60px) saturate(1.3)', opacity: 0.75}}>
        <Cover i={track} size={560} radius={0} />
      </div>
      <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(180deg, rgba(11,23,32,0.25), rgba(11,23,32,0.85) 70%)'}} />
      <div style={{position: 'absolute', inset: 0, padding: '64px 28px 0', display: 'flex', flexDirection: 'column'}}>
        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
          <div style={font(15, 600, COLORS.muted)}>Sonando ahora</div>
          {premium && (
            <div style={{...font(13, 700, '#cfe8ff'), padding: '5px 12px', borderRadius: 99, background: 'rgba(30,144,240,0.3)', border: '1px solid rgba(120,190,255,0.4)'}}>
              Premium
            </div>
          )}
        </div>
        {/* carátula con brillo */}
        <div style={{marginTop: 26, position: 'relative', transform: `scale(${breathe})`}}>
          <div style={{position: 'absolute', inset: 20, filter: 'blur(36px)', opacity: 0.9}}>
            <Cover i={track} size={300} radius={30} />
          </div>
          <Cover i={track} size={340} radius={26} logo={track === 0} style={{boxShadow: '0 30px 60px rgba(0,0,0,0.5)'}} />
          <div style={{position: 'absolute', inset: 0, borderRadius: 26, background: 'linear-gradient(135deg, rgba(255,255,255,0.28), transparent 40%)'}} />
        </div>
        <div style={{marginTop: 30, ...font(27, 700), fontFamily: FONT_TITLE}}>{tr.t}</div>
        <div style={{marginTop: 4, ...font(18, 500, COLORS.muted)}}>{tr.a}</div>
        <div style={{marginTop: 26, height: 6, borderRadius: 6, background: 'rgba(255,255,255,0.15)'}}>
          <div style={{width: `${progress * 100}%`, height: '100%', borderRadius: 6, background: `linear-gradient(90deg, ${COLORS.accent}, ${COLORS.cyan})`, boxShadow: `0 0 12px ${COLORS.cyan}`}} />
        </div>
        <div style={{display: 'flex', justifyContent: 'space-between', marginTop: 8, ...font(13, 500, COLORS.muted)}}>
          <span>{fmt(progress * 221)}</span>
          <span>3:41</span>
        </div>
        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 22, padding: '0 14px'}}>
          <IconSkip size={24} color={COLORS.muted} />
          <IconPrev size={30} color={COLORS.text} />
          <div style={{width: 78, height: 78, borderRadius: 99, background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 10px 30px rgba(255,255,255,0.25)'}}>
            <IconPause size={32} color={COLORS.bg} />
          </div>
          <div style={{transform: `scale(${1 + pulseNext * 0.35})`, filter: `drop-shadow(0 0 ${pulseNext * 18}px ${COLORS.cyan})`}}>
            <IconNext size={30} color={pulseNext > 0.05 ? COLORS.cyan : COLORS.text} />
          </div>
          <EqBars n={3} w={4} h={20} />
        </div>
      </div>
      {overlay}
    </div>
  );
};

const fmt = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

export const HomeScreen: React.FC<{highlight?: 'top' | 'recos' | null; scroll?: number}> = ({highlight = null, scroll = 0}) => {
  const hl = (k: string): React.CSSProperties =>
    highlight === k ? {boxShadow: `0 0 0 2px ${COLORS.accent2}, 0 0 40px rgba(30,144,240,0.5)`, borderRadius: 22} : {};
  return (
    <div style={{position: 'absolute', inset: 0, background: `radial-gradient(120% 60% at 30% 0%, #123049, ${COLORS.bg} 60%)`}}>
      <div style={{padding: '66px 24px 0', transform: `translateY(${-scroll}px)`}}>
        <div style={{display: 'flex', alignItems: 'center', gap: 10}}>
          <Logo size={34} glow={0.3} />
          <div style={{...font(22, 800), fontFamily: FONT_TITLE}}>Marea</div>
        </div>
        <div style={{marginTop: 20, ...font(30, 800), fontFamily: FONT_TITLE, letterSpacing: -1}}>Buenas noches</div>
        <div style={{...font(16, 500, COLORS.muted)}}>¿Qué quieres escuchar hoy?</div>
        <div style={{marginTop: 16, padding: '12px 16px', borderRadius: 16, background: 'rgba(255,255,255,0.07)', border: '1px solid rgba(255,255,255,0.1)', ...font(15, 500, COLORS.muted)}}>
          Canciones, artistas o un link
        </div>
        {/* Tu top */}
        <div style={{marginTop: 20, padding: 12, ...hl('top')}}>
          <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'baseline'}}>
            <div style={{...font(20, 800), fontFamily: FONT_TITLE}}>Tu top</div>
            <div style={font(13, 600, COLORS.accent2)}>Reproducir tu top</div>
          </div>
          <div style={font(13, 500, COLORS.muted)}>Lo que más has escuchado</div>
          {[1, 2, 3].map((n, i) => (
            <div key={n} style={{display: 'flex', alignItems: 'center', gap: 12, marginTop: 10}}>
              <div style={{width: 18, ...font(15, 800, i === 0 ? COLORS.cyan : COLORS.muted)}}>{n}</div>
              <Cover i={i + 1} size={46} radius={10} />
              <div>
                <div style={font(15, 700)}>{TRACKS[i + 1].t}</div>
                <div style={font(12, 500, COLORS.muted)}>{TRACKS[i + 1].a}</div>
              </div>
            </div>
          ))}
        </div>
        {/* Recomendado */}
        <div style={{marginTop: 10, padding: 12, ...hl('recos')}}>
          <div style={{...font(20, 800), fontFamily: FONT_TITLE}}>Recomendado para ti</div>
          <div style={font(13, 500, COLORS.muted)}>De tus artistas</div>
          <div style={{display: 'flex', gap: 10, marginTop: 10}}>
            {[4, 5, 6].map((c) => (
              <div key={c}>
                <Cover i={c} size={104} radius={14} />
                <div style={{marginTop: 6, ...font(12, 600)}}>{TRACKS[c % 6].t.split(' ')[0]}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
      {/* mini player */}
      <div style={{position: 'absolute', left: 14, right: 14, bottom: 18, padding: 10, borderRadius: 18, display: 'flex', alignItems: 'center', gap: 12, background: 'rgba(20,40,58,0.85)', border: '1px solid rgba(255,255,255,0.12)', backdropFilter: 'blur(20px)'}}>
        <Cover i={0} size={44} radius={10} logo />
        <div style={{flex: 1}}>
          <div style={font(14, 700)}>{TRACKS[0].t}</div>
          <div style={font(12, 500, COLORS.muted)}>{TRACKS[0].a}</div>
        </div>
        <EqBars n={3} w={4} h={18} />
        <IconPause size={22} color={COLORS.text} />
      </div>
    </div>
  );
};

const LYRICS = [
  'Cuando la noche se apaga',
  'y el mar me trae tu voz,',
  'sigo la luz de la marea',
  'que me regresa a ti.',
  'Olas de medianoche,',
  'canta conmigo otra vez.',
];

/** Letra sincronizada palabra por palabra. `t` = progreso 0..1 de la letra. */
export const LyricsScreen: React.FC<{t: number}> = ({t}) => {
  const words = LYRICS.map((l) => l.split(' '));
  const total = words.reduce((a, w) => a + w.length, 0);
  const pos = t * total;
  let idx = 0;
  let activeLine = 0;
  words.forEach((w, li) => {
    if (pos >= idx) activeLine = li;
    idx += w.length;
  });
  const lineH = 76;
  idx = 0;
  return (
    <div style={{position: 'absolute', inset: 0}}>
      <div style={{position: 'absolute', inset: -80, filter: 'blur(70px) saturate(1.4)', opacity: 0.7}}>
        <Cover i={0} size={560} radius={0} />
      </div>
      <div style={{position: 'absolute', inset: 0, background: 'rgba(8,18,28,0.55)'}} />
      <div style={{position: 'absolute', top: 62, left: 26, right: 26, display: 'flex', alignItems: 'center', gap: 12}}>
        <Cover i={0} size={52} radius={12} logo />
        <div>
          <div style={font(16, 700)}>{TRACKS[0].t}</div>
          <div style={font(13, 500, COLORS.muted)}>Letra sincronizada</div>
        </div>
      </div>
      <div style={{position: 'absolute', top: 300 - activeLine * lineH, left: 26, right: 22}}>
        {words.map((ws, li) => {
          const lineStart = idx;
          idx += ws.length;
          return (
            <div
              key={li}
              style={{
                height: lineH,
                ...font(27, 800),
                fontFamily: FONT_TITLE,
                letterSpacing: -0.6,
                lineHeight: 1.1,
                opacity: li === activeLine ? 1 : li < activeLine ? 0.35 : 0.5,
                filter: li === activeLine ? 'none' : 'blur(0.6px)',
              }}
            >
              {ws.map((w, wi) => {
                const k = lineStart + wi;
                const lit = interpolate(pos, [k, k + 0.6], [0, 1], clamp);
                return (
                  <span
                    key={wi}
                    style={{
                      marginRight: 8,
                      color: lit > 0.5 ? '#fff' : 'rgba(255,255,255,0.45)',
                      textShadow: lit > 0.5 && li === activeLine ? `0 0 18px rgba(47,212,230,0.8)` : 'none',
                    }}
                  >
                    {w}
                  </span>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
};

const BANDS = ['60', '230', '910', '3.6k', '14k'];
export const EqScreen: React.FC<{p: number}> = ({p}) => {
  const frame = useCurrentFrame();
  const target = [0.7, 0.45, 0.3, 0.55, 0.8];
  return (
    <div style={{position: 'absolute', inset: 0, background: `radial-gradient(120% 70% at 50% 0%, #133550, ${COLORS.bg} 65%)`}}>
      <div style={{padding: '70px 26px 0'}}>
        <div style={{...font(28, 800), fontFamily: FONT_TITLE}}>Ecualizador</div>
        <div style={{display: 'flex', gap: 8, marginTop: 14, flexWrap: 'wrap'}}>
          {['Plano', 'Graves', 'Vocal', 'Personal'].map((x, i) => (
            <div key={x} style={{...font(13, 700, i === 3 ? '#fff' : COLORS.muted), padding: '7px 14px', borderRadius: 99, background: i === 3 ? COLORS.accent : 'rgba(255,255,255,0.07)'}}>
              {x}
            </div>
          ))}
        </div>
        <div style={{display: 'flex', justifyContent: 'space-between', marginTop: 40, height: 380, padding: '0 6px'}}>
          {BANDS.map((b, i) => {
            const v = interpolate(p, [0, 1], [0.5, target[i]], clamp) + Math.sin(frame / 14 + i) * 0.03 * p;
            return (
              <div key={b} style={{display: 'flex', flexDirection: 'column', alignItems: 'center', width: 50}}>
                <div style={{position: 'relative', flex: 1, width: 8, borderRadius: 8, background: 'rgba(255,255,255,0.1)'}}>
                  <div style={{position: 'absolute', bottom: 0, width: '100%', height: `${v * 100}%`, borderRadius: 8, background: `linear-gradient(0deg, ${COLORS.accent}, ${COLORS.cyan})`, boxShadow: `0 0 14px rgba(47,212,230,0.6)`}} />
                  <div style={{position: 'absolute', left: -12, bottom: `calc(${v * 100}% - 16px)`, width: 32, height: 32, borderRadius: 99, background: '#fff', boxShadow: '0 4px 14px rgba(0,0,0,0.4)'}} />
                </div>
                <div style={{marginTop: 14, ...font(13, 600, COLORS.muted)}}>{b}</div>
              </div>
            );
          })}
        </div>
        <div style={{marginTop: 34, padding: 16, borderRadius: 18, background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)'}}>
          <div style={font(15, 700)}>Refuerzo de graves</div>
          <div style={{marginTop: 10, height: 6, borderRadius: 6, background: 'rgba(255,255,255,0.12)'}}>
            <div style={{width: `${30 + p * 40}%`, height: '100%', borderRadius: 6, background: COLORS.accent2}} />
          </div>
        </div>
      </div>
    </div>
  );
};
