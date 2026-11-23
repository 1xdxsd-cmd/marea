import React from 'react';
import {AbsoluteFill, Img, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {COLORS, DOMAIN, FONT_TEXT, FONT_TITLE, PLANS, SceneDef, beat} from './config';
import {EqScreen, HomeScreen, LyricsScreen, PlayerScreen, TRACKS} from './screens';
import {
  Cover,
  EqBars,
  Glass,
  Grad,
  IconCheck,
  IconDownload,
  IconEq,
  IconNoAds,
  IconSkip,
  IconSpark,
  IconUser,
  IconWifiOff,
  Logo,
  Phone,
  Pill,
  Text,
  Title,
  Words,
  clamp,
  ease,
  rise,
  useIn,
} from './ui';

type P = {scene: SceneDef};

const center: React.CSSProperties = {display: 'flex', alignItems: 'center', justifyContent: 'center'};

// ─────────────────────────────────────────────────────────────
// 1 · Intro
// ─────────────────────────────────────────────────────────────
export const IntroScene: React.FC<P> = ({scene}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const b1 = beat(scene, 1);
  const qOut = ease(frame, b1 - 8, b1 + 6);
  const logoIn = spring({frame: frame - b1 + 4, fps, config: {damping: 11, stiffness: 90}});
  const word = useIn(b1 + 10, 20);
  const orbit = ease(frame, b1, b1 + 30);

  return (
    <AbsoluteFill>
      {/* Pregunta */}
      <AbsoluteFill
        style={{
          ...center,
          flexDirection: 'column',
          opacity: 1 - qOut,
          transform: `scale(${1 + qOut * 0.25})`,
          filter: `blur(${qOut * 16}px)`,
        }}
      >
        <Title size={128} style={{textAlign: 'center', maxWidth: 1500}}>
          <Words text="¿Y si tu música" start={4} every={4} />
          <br />
          <Words text="no tuviera límites?" start={20} every={5} accentFrom={2} />
        </Title>
      </AbsoluteFill>

      {/* Carátulas orbitando */}
      {Array.from({length: 8}).map((_, i) => {
        const a = (i / 8) * Math.PI * 2 + frame / 90;
        const rx = 720;
        const ry = 300;
        const z = (Math.sin(a) + 1) / 2;
        const s = 0.55 + z * 0.55;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: 960 + Math.cos(a) * rx * (0.6 + orbit * 0.4) - 80,
              top: 470 + Math.sin(a) * ry - 80,
              transform: `scale(${s * orbit})`,
              opacity: orbit * (0.35 + z * 0.6) * interpolate(Math.abs(Math.cos(a)), [0.15, 0.55], [0.12, 1], clamp),
              filter: `blur(${(1 - z) * 4}px)`,
              zIndex: Math.round(z * 10),
            }}
          >
            <Cover i={i} size={160} radius={24} style={{boxShadow: '0 20px 50px rgba(0,0,0,0.5)'}} />
          </div>
        );
      })}

      {/* Logo */}
      <AbsoluteFill style={{...center, flexDirection: 'column', zIndex: 20}}>
        <div style={{position: 'relative', ...center}}>
          {[0, 1, 2].map((r) => {
            const t = ((frame - b1 - r * 14) % 60) / 60;
            return frame > b1 + r * 14 ? (
              <div
                key={r}
                style={{
                  position: 'absolute',
                  width: 260,
                  height: 260,
                  borderRadius: '50%',
                  border: `2px solid ${COLORS.cyan}`,
                  transform: `scale(${1 + t * 1.2})`,
                  opacity: (1 - t) * 0.5 * logoIn,
                }}
              />
            ) : null;
          })}
          <Logo size={260} glow={1.2} style={{transform: `scale(${logoIn}) rotate(${(1 - logoIn) * -40}deg)`, opacity: Math.min(1, logoIn * 2)}} />
        </div>
        <Title size={150} style={{marginTop: 28, ...rise(word, 50), letterSpacing: interpolate(word, [0, 1], [10, -5])}}>
          Marea
        </Title>
        <Text size={40} style={{marginTop: 12, color: COLORS.text, fontWeight: 500}}>
          <Words text="Todo lo que amas, a tu manera." start={b1 + 26} every={4} accentFrom={4} />
        </Text>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

// ─────────────────────────────────────────────────────────────
// 2 · Gratis → Premium + beneficios
// ─────────────────────────────────────────────────────────────
const FEATURES = [
  {icon: IconNoAds, t: 'Sin anuncios', d: 'Nada interrumpe tu música.'},
  {icon: IconSkip, t: 'Saltos ilimitados', d: 'Pasa de canción sin esperar.'},
  {icon: IconDownload, t: 'Descargas', d: 'Escucha incluso sin internet.'},
  {icon: IconEq, t: 'Ecualizador profesional', d: 'Cada tema, como tú quieres.'},
];

const PlanCard: React.FC<{premium?: boolean; p: number; focus: number}> = ({premium, p, focus}) => {
  const items = premium
    ? ['Sin anuncios', 'Saltos ilimitados', 'Descargas sin conexión', 'Ecualizador profesional']
    : ['Escucha lo que quieras', 'Con anuncios', 'Saltos limitados', 'Solo en línea'];
  const dim = premium ? 1 : 1 - focus * 0.55;
  return (
    <Glass
      glow={premium ? focus : 0}
      style={{
        width: 470,
        padding: 44,
        ...rise(p, 80),
        opacity: p * dim,
        transform: `translateY(${(1 - p) * 80}px) scale(${premium ? 1 + focus * 0.08 : 1 - focus * 0.05})`,
        background: premium
          ? `linear-gradient(160deg, rgba(30,144,240,${0.12 + focus * 0.2}), rgba(255,255,255,0.03))`
          : undefined,
      }}
    >
      <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
        <Title size={52}>{premium ? <Grad>Premium</Grad> : 'Gratis'}</Title>
        {premium ? <IconSpark size={40} color={COLORS.cyan} /> : <Pill style={{fontSize: 18, padding: '6px 14px'}}>Con anuncios</Pill>}
      </div>
      <div style={{marginTop: 30}}>
        {items.map((it, i) => (
          <div key={it} style={{display: 'flex', alignItems: 'center', gap: 16, marginTop: i ? 18 : 0}}>
            <div style={{width: 34, height: 34, borderRadius: 99, ...center, background: premium ? COLORS.accent : 'rgba(255,255,255,0.1)'}}>
              <IconCheck size={20} color="#fff" />
            </div>
            <Text size={28} style={{color: COLORS.text}}>
              {it}
            </Text>
          </div>
        ))}
      </div>
    </Glass>
  );
};

export const PremiumScene: React.FC<P> = ({scene}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const [b0, b1, b2, b3, b4, b5] = [0, 1, 2, 3, 4, 5].map((i) => beat(scene, i));

  // fase A → fase B
  const swap = ease(frame, b2 - 14, b2 + 6);
  const tA = useIn(b0 + 2);
  const focus = ease(frame, b1, b1 + 18);
  const phoneIn = spring({frame: frame - b2 + 10, fps, config: {damping: 16, stiffness: 90}});

  // saltos: cada 22 frames cambia de canción
  const skipT = frame - b3 - 20;
  const skips = frame >= b3 + 20 && frame < b4 ? Math.min(5, Math.floor(skipT / 22) + 1) : frame >= b4 ? 5 : 0;
  const pulse = frame >= b3 + 20 && frame < b4 ? Math.max(0, 1 - (skipT % 22) / 10) : 0;
  const track = skips % TRACKS.length;
  const progress = frame < b3 ? 0.18 + frame / 2500 : 0.05 + ((skipT % 22) / 22) * 0.06 + (frame >= b4 ? (frame - b4) / 1500 : 0);

  // anuncio que desaparece
  const adOut = ease(frame, b2 + 10, b2 + 30);
  const adP = frame < b2 + 40 ? 1 - adOut : 0;

  // descarga
  const dl = ease(frame, b4 + 12, b4 + 70);
  const dlIn = ease(frame, b4, b4 + 14) * (1 - ease(frame, b5 - 10, b5));

  // eq
  const eqIn = ease(frame, b5, b5 + 16);
  const eqP = ease(frame, b5 + 12, b5 + 70);

  const active = frame >= b5 ? 3 : frame >= b4 ? 2 : frame >= b3 ? 1 : frame >= b2 ? 0 : -1;

  return (
    <AbsoluteFill>
      {/* ── Fase A ── */}
      <AbsoluteFill style={{opacity: 1 - swap, transform: `scale(${1 - swap * 0.06})`, filter: `blur(${swap * 10}px)`}}>
        <div style={{position: 'absolute', left: 150, top: 300, width: 620}}>
          <Title size={104} style={rise(tA)}>
            Empieza
            <br />
            <Grad>gratis.</Grad>
          </Title>
          <Text size={36} style={{marginTop: 30, ...rise(ease(frame, b0 + 30, b0 + 50))}}>
            Escucha lo que quieras, con anuncios.
          </Text>
          <Text size={36} style={{marginTop: 14, color: COLORS.text, fontWeight: 700, ...rise(focus)}}>
            Y cuando estés listo, pásate a <Grad>Premium</Grad>.
          </Text>
        </div>
        <div style={{position: 'absolute', right: 130, top: 230, display: 'flex', gap: 36, alignItems: 'center'}}>
          <PlanCard p={useIn(b0 + 10)} focus={focus} />
          <PlanCard premium p={useIn(b0 + 18)} focus={focus} />
        </div>
      </AbsoluteFill>

      {/* ── Fase B ── */}
      {frame > b2 - 16 && (
        <AbsoluteFill style={{opacity: swap}}>
          <div
            style={{
              position: 'absolute',
              left: 330,
              top: 70,
              transform: `perspective(1800px) rotateY(${12 - phoneIn * 4 + Math.sin(frame / 60) * 2}deg) translateY(${(1 - phoneIn) * 200}px)`,
            }}
          >
            <Phone width={420}>
              <PlayerScreen track={track} progress={progress} pulseNext={pulse} />
              {/* anuncio */}
              {adP > 0 && (
                <div
                  style={{
                    position: 'absolute',
                    left: 16,
                    right: 16,
                    top: 70,
                    padding: 16,
                    borderRadius: 18,
                    background: 'rgba(255,200,80,0.95)',
                    color: '#2a1a00',
                    fontFamily: FONT_TEXT,
                    fontWeight: 800,
                    fontSize: 18,
                    display: 'flex',
                    justifyContent: 'space-between',
                    transform: `translateY(${-adOut * 160}px) scale(${1 - adOut * 0.2})`,
                    opacity: 1 - adOut,
                  }}
                >
                  <span>Anuncio</span>
                  <span>0:15</span>
                </div>
              )}
              {/* descarga */}
              {dlIn > 0 && (
                <div
                  style={{
                    position: 'absolute',
                    left: 16,
                    right: 16,
                    bottom: 26,
                    padding: 18,
                    borderRadius: 22,
                    background: 'rgba(16,34,50,0.92)',
                    border: '1px solid rgba(255,255,255,0.14)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 14,
                    transform: `translateY(${(1 - dlIn) * 140}px)`,
                    opacity: dlIn,
                  }}
                >
                  <svg width={50} height={50} viewBox="0 0 50 50">
                    <circle cx={25} cy={25} r={21} stroke="rgba(255,255,255,0.15)" strokeWidth={5} fill="none" />
                    <circle
                      cx={25}
                      cy={25}
                      r={21}
                      stroke={COLORS.cyan}
                      strokeWidth={5}
                      fill="none"
                      strokeDasharray={132}
                      strokeDashoffset={132 * (1 - dl)}
                      strokeLinecap="round"
                      transform="rotate(-90 25 25)"
                    />
                    <g transform="translate(13 13)">{dl < 1 ? <IconDownload size={24} color="#fff" /> : <IconCheck size={24} color={COLORS.cyan} />}</g>
                  </svg>
                  <div style={{fontFamily: FONT_TEXT}}>
                    <div style={{fontSize: 17, fontWeight: 700, color: COLORS.text}}>{dl < 1 ? `Descargando… ${Math.round(dl * 100)}%` : 'Disponible sin conexión'}</div>
                    <div style={{fontSize: 14, color: COLORS.muted, display: 'flex', alignItems: 'center', gap: 6}}>
                      <IconWifiOff size={16} color={COLORS.muted} /> Escúchala sin internet
                    </div>
                  </div>
                </div>
              )}
              {eqIn > 0 && (
                <div style={{position: 'absolute', inset: 0, opacity: eqIn, transform: `translateY(${(1 - eqIn) * 60}px)`}}>
                  <EqScreen p={eqP} />
                </div>
              )}
            </Phone>
          </div>

          {/* tarjetas flotantes alrededor del teléfono */}
          <div style={{position: 'absolute', left: 150, top: 250, ...rise(ease(frame, b2 + 20, b2 + 36))}}>
            <Pill>
              <IconNoAds size={22} color={COLORS.cyan} /> 0 anuncios
            </Pill>
          </div>
          <div style={{position: 'absolute', left: 700, top: 620, ...rise(ease(frame, b3 + 20, b3 + 36))}}>
            <Pill>
              <IconSkip size={22} color={COLORS.cyan} /> {skips} saltos · sin límite
            </Pill>
          </div>
          <div style={{position: 'absolute', left: 170, top: 760, ...rise(ease(frame, b5 + 10, b5 + 26))}}>
            <Pill>
              <EqBars n={4} w={4} h={18} /> 5 bandas
            </Pill>
          </div>

          {/* lista de beneficios */}
          <div style={{position: 'absolute', left: 1000, top: 150, width: 800}}>
            <div style={rise(ease(frame, b2 - 4, b2 + 14))}>
              <Pill active>
                <IconSpark size={20} color="#fff" /> Marea Premium
              </Pill>
            </div>
            {FEATURES.map((f, i) => {
              const at = [b2, b3, b4, b5][i];
              const p = ease(frame, at - 4, at + 14);
              const isActive = active === i;
              const Icon = f.icon;
              return (
                <div
                  key={f.t}
                  style={{
                    marginTop: i ? 22 : 36,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 26,
                    padding: '22px 28px',
                    borderRadius: 28,
                    background: isActive ? 'rgba(30,144,240,0.16)' : 'rgba(255,255,255,0.03)',
                    border: `1.5px solid ${isActive ? 'rgba(90,180,255,0.55)' : 'rgba(255,255,255,0.07)'}`,
                    boxShadow: isActive ? '0 0 50px rgba(30,144,240,0.3)' : 'none',
                    opacity: 0.25 + p * (isActive ? 0.75 : 0.45),
                    transform: `translateX(${(1 - p) * 60}px) scale(${isActive ? 1.03 : 1})`,
                    transformOrigin: 'left center',
                  }}
                >
                  <div
                    style={{
                      width: 76,
                      height: 76,
                      borderRadius: 22,
                      ...center,
                      background: p > 0.5 ? `linear-gradient(135deg, ${COLORS.accent2}, ${COLORS.accent})` : 'rgba(255,255,255,0.08)',
                    }}
                  >
                    <Icon size={38} color="#fff" />
                  </div>
                  <div>
                    <Title size={44}>{f.t}</Title>
                    <Text size={26} style={{marginTop: 4}}>
                      {f.d}
                    </Text>
                  </div>
                </div>
              );
            })}
          </div>
        </AbsoluteFill>
      )}
    </AbsoluteFill>
  );
};

// ─────────────────────────────────────────────────────────────
// 3 · Planes
// ─────────────────────────────────────────────────────────────
export const PlanesScene: React.FC<P> = ({scene}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const [b0, b1, b2] = [0, 1, 2].map((i) => beat(scene, i));
  const tIn = useIn(b0);
  const share = ease(frame, b2, b2 + 30);
  const delays = [b1 - 4, b1 + 22, b1 + 36];

  return (
    <AbsoluteFill>
      <div style={{position: 'absolute', top: 90, width: '100%', textAlign: 'center'}}>
        <Title size={92} style={rise(tIn)}>
          Elige el plan que <Grad>va contigo</Grad>
        </Title>
        <Text size={32} style={{marginTop: 14, ...rise(share, 20)}}>
          Comparte la experiencia con las personas que más quieres.
        </Text>
      </div>
      <div style={{position: 'absolute', top: 340, width: '100%', display: 'flex', justifyContent: 'center', gap: 40}}>
        {PLANS.map((pl, i) => {
          const p = spring({frame: frame - delays[i], fps, config: {damping: 14, stiffness: 110}});
          const hl = i === 2 ? share : 0;
          return (
            <Glass
              key={pl.name}
              glow={hl}
              style={{
                width: 500,
                padding: '42px 44px',
                opacity: p,
                transform: `translateY(${(1 - p) * 120}px) rotateX(${(1 - p) * 30}deg) scale(${1 + hl * 0.05})`,
                background: i === 2 ? `linear-gradient(160deg, rgba(30,144,240,${0.1 + hl * 0.2}), rgba(255,255,255,0.03))` : undefined,
              }}
            >
              <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                <Title size={48}>{pl.name}</Title>
                <Pill style={{fontSize: 18, padding: '6px 14px'}} active={i === 2}>
                  {pl.note}
                </Pill>
              </div>
              <div style={{display: 'flex', alignItems: 'baseline', gap: 10, marginTop: 26}}>
                <Title size={120} style={{letterSpacing: -5}}>
                  <Grad>${Math.round(interpolate(p, [0, 1], [0, pl.price], clamp))}</Grad>
                </Title>
                <Text size={26} style={{whiteSpace: 'nowrap'}}>MXN / mes</Text>
              </div>
              {/* personas */}
              <div style={{display: 'flex', marginTop: 28, height: 70, alignItems: 'center'}}>
                {Array.from({length: pl.seats}).map((_, k) => {
                  const ap = ease(frame, b2 + k * 4 + i * 6, b2 + 14 + k * 4 + i * 6);
                  const show = k === 0 ? 1 : ap;
                  return (
                    <div
                      key={k}
                      style={{
                        width: 62,
                        height: 62,
                        borderRadius: 99,
                        marginLeft: k ? -14 : 0,
                        ...center,
                        background: `linear-gradient(135deg, hsl(${200 + k * 22} 80% 60%), hsl(${215 + k * 22} 75% 42%))`,
                        border: `3px solid ${COLORS.bg}`,
                        transform: `scale(${show})`,
                        opacity: show,
                        boxShadow: k > 0 ? `0 0 ${20 * ap}px rgba(47,212,230,0.6)` : 'none',
                      }}
                    >
                      <IconUser size={30} color="#fff" />
                    </div>
                  );
                })}
              </div>
              <div style={{marginTop: 26, height: 1.5, background: 'rgba(255,255,255,0.1)'}} />
              <Text size={24} style={{marginTop: 20, color: COLORS.text}}>
                Todo Premium{pl.seats > 1 ? `, para ${pl.seats === 2 ? 'dos' : 'toda la familia'}` : ', solo para ti'}
              </Text>
            </Glass>
          );
        })}
      </div>
    </AbsoluteFill>
  );
};

// ─────────────────────────────────────────────────────────────
// 4 · Tu top, recomendaciones y letras
// ─────────────────────────────────────────────────────────────
export const DescubreScene: React.FC<P> = ({scene, }) => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const [b0, b1, b2, b3] = [0, 1, 2, 3].map((i) => beat(scene, i));
  const tIn = useIn(b0);
  const up = ease(frame, b1 - 10, b1 + 10);
  const topIn = ease(frame, b1 - 6, b1 + 16);
  const recIn = ease(frame, b2 - 6, b2 + 16);
  const lyrIn = ease(frame, b1 - 4, b1 + 18);
  const lyrSwap = ease(frame, b3 - 8, b3 + 8);
  const lyrT = interpolate(frame, [b3, b3 + 200], [0.02, 0.85], clamp);
  void durationInFrames;

  return (
    <AbsoluteFill>
      <div
        style={{
          position: 'absolute',
          width: '100%',
          textAlign: 'center',
          top: interpolate(up, [0, 1], [400, 60]),
          transform: `scale(${interpolate(up, [0, 1], [1.25, 0.7])})`,
        }}
      >
        <Title size={120} style={rise(tIn)}>
          Marea <Grad>aprende de ti.</Grad>
        </Title>
      </div>

      {/* Tu top */}
      <Glass style={{position: 'absolute', left: 110, top: 250, width: 560, padding: 36, ...rise(topIn, 80)}}>
        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
          <Title size={50}>Tu top</Title>
          <Pill active style={{fontSize: 18, padding: '6px 14px'}}>Tu #1</Pill>
        </div>
        <Text size={22}>Lo que más has escuchado</Text>
        {[1, 2, 3, 4, 5].map((n, i) => {
          const p = ease(frame, b1 + i * 5, b1 + 14 + i * 5);
          return (
            <div key={n} style={{display: 'flex', alignItems: 'center', gap: 18, marginTop: 18, opacity: p, transform: `translateX(${(1 - p) * -40}px)`}}>
              <Title size={30} style={{width: 30, color: i === 0 ? COLORS.cyan : COLORS.muted}}>
                {n}
              </Title>
              <Cover i={i + 1} size={62} radius={12} />
              <div style={{flex: 1}}>
                <Text size={24} style={{color: COLORS.text, fontWeight: 700}}>
                  {TRACKS[(i + 1) % 6].t}
                </Text>
                <div style={{marginTop: 6, height: 5, borderRadius: 5, background: 'rgba(255,255,255,0.08)'}}>
                  <div style={{width: `${(1 - i * 0.16) * 100 * p}%`, height: '100%', borderRadius: 5, background: `linear-gradient(90deg, ${COLORS.accent}, ${COLORS.cyan})`}} />
                </div>
              </div>
            </div>
          );
        })}
      </Glass>

      {/* Letras */}
      <div style={{position: 'absolute', left: 750, top: 205, opacity: lyrIn, transform: `translateY(${(1 - lyrIn) * 260}px) scale(${(0.9 + lyrIn * 0.1) * 0.9})`, transformOrigin: 'top center'}}>
        <Phone width={420}>
          <HomeScreen highlight={frame >= b2 ? 'recos' : 'top'} />
          {lyrSwap > 0 && (
            <div style={{position: 'absolute', inset: 0, background: COLORS.bg, opacity: lyrSwap, transform: `scale(${1.08 - lyrSwap * 0.08})`}}>
              <LyricsScreen t={lyrT} />
            </div>
          )}
        </Phone>
        <div style={{position: 'absolute', right: -110, top: 150, ...rise(ease(frame, b3 + 10, b3 + 26))}}>
          <Pill active>
            <IconSpark size={18} color="#fff" /> Letra sincronizada
          </Pill>
        </div>
      </div>

      {/* Recomendaciones */}
      <Glass style={{position: 'absolute', right: 110, top: 250, width: 560, padding: 36, ...rise(recIn, 80)}}>
        <Title size={44}>Recomendado para ti</Title>
        <Text size={22}>De tus artistas favoritos</Text>
        <div style={{display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18, marginTop: 22}}>
          {[4, 5, 6, 7].map((c, i) => {
            const p = ease(frame, b2 + i * 5, b2 + 16 + i * 5);
            return (
              <div key={c} style={{opacity: p, transform: `scale(${0.8 + p * 0.2})`}}>
                <Cover i={c} size={226} radius={18} />
                <Text size={20} style={{marginTop: 8, color: COLORS.text, fontWeight: 700}}>
                  {['Más de Lía Montero', 'Mix Los Faros', 'Nube Azul y más', 'Radio Mar Abierto'][i]}
                </Text>
              </div>
            );
          })}
        </div>
      </Glass>
    </AbsoluteFill>
  );
};

// ─────────────────────────────────────────────────────────────
// 5 · Diseño
// ─────────────────────────────────────────────────────────────
export const DisenoScene: React.FC<P> = () => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const push = interpolate(frame, [0, durationInFrames], [1, 1.07]);
  const a = useIn(0, 20);
  const b = useIn(6, 20);
  const c = useIn(12, 20);
  const fl = (k: number) => Math.sin(frame / 25 + k) * 10;
  return (
    <AbsoluteFill style={{transform: `scale(${push})`}}>
      <div style={{position: 'absolute', top: 70, width: '100%', textAlign: 'center'}}>
        <Title size={84} style={rise(a)}>
          Elegante. <Grad>Fluido.</Grad> Hecho para disfrutarse.
        </Title>
      </div>
      {/* landing */}
      <div
        style={{
          position: 'absolute',
          left: 410,
          top: 250,
          width: 1100,
          opacity: a,
          transform: `perspective(2000px) rotateX(${8 - a * 4}deg) translateY(${(1 - a) * 100 + fl(0)}px)`,
        }}
      >
        <div style={{borderRadius: 22, overflow: 'hidden', border: '1.5px solid rgba(255,255,255,0.14)', boxShadow: '0 50px 120px rgba(0,0,0,0.6), 0 0 80px rgba(30,144,240,0.2)'}}>
          <div style={{height: 40, background: '#13222e', display: 'flex', alignItems: 'center', gap: 8, padding: '0 16px'}}>
            {['#ff5f57', '#febc2e', '#28c840'].map((col) => (
              <div key={col} style={{width: 12, height: 12, borderRadius: 99, background: col}} />
            ))}
            <div style={{marginLeft: 20, flex: 1, height: 24, borderRadius: 8, background: 'rgba(255,255,255,0.06)', fontFamily: FONT_TEXT, fontSize: 15, color: COLORS.muted, display: 'flex', alignItems: 'center', paddingLeft: 12}}>
              {DOMAIN}
            </div>
          </div>
          <Img src={staticFile('landing.png')} style={{width: '100%', display: 'block'}} />
        </div>
      </div>
      <div style={{position: 'absolute', left: 120, top: 330, transform: `rotate(-6deg) translateY(${(1 - b) * 300 + fl(1)}px)`, opacity: b}}>
        <Phone width={320}>
          <div style={{position: 'absolute', inset: 0, transform: 'scale(0.761)', transformOrigin: 'top left', width: 396, height: 849}}>
            <HomeScreen />
          </div>
        </Phone>
      </div>
      <div style={{position: 'absolute', right: 120, top: 300, transform: `rotate(6deg) translateY(${(1 - c) * 300 + fl(2)}px)`, opacity: c}}>
        <Phone width={320}>
          <div style={{position: 'absolute', inset: 0, transform: 'scale(0.761)', transformOrigin: 'top left', width: 396, height: 849}}>
            <PlayerScreen progress={0.4 + frame / 900} />
          </div>
        </Phone>
      </div>
    </AbsoluteFill>
  );
};

// ─────────────────────────────────────────────────────────────
// 6 · CTA
// ─────────────────────────────────────────────────────────────
export const CtaScene: React.FC<P> = ({scene}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const [b0, b1, b2] = [0, 1, 2].map((i) => beat(scene, i));
  const logo = spring({frame: frame - b0, fps, config: {damping: 12, stiffness: 100}});
  const name = useIn(b0 + 6);
  const tag = useIn(b1);
  const btn = spring({frame: frame - b2, fps, config: {damping: 13, stiffness: 120}});
  const shimmer = ((frame - b2) % 70) / 70;
  return (
    <AbsoluteFill style={{...center, flexDirection: 'column'}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 40, marginTop: -60}}>
        <Logo size={190} glow={1.3} style={{transform: `scale(${logo}) rotate(${(1 - logo) * 60}deg)`}} />
        <Title size={190} style={{...rise(name, 40), letterSpacing: -8}}>
          Marea
        </Title>
      </div>
      <Title size={76} style={{marginTop: 26, ...rise(tag)}}>
        Tu música, <Grad>sin límites.</Grad>
      </Title>
      <div style={{marginTop: 56, display: 'flex', flexDirection: 'column', alignItems: 'center', opacity: btn, transform: `scale(${0.7 + btn * 0.3})`}}>
        <div
          style={{
            position: 'relative',
            overflow: 'hidden',
            padding: '30px 64px',
            borderRadius: 99,
            display: 'flex',
            alignItems: 'center',
            gap: 18,
            background: `linear-gradient(135deg, ${COLORS.accent2}, ${COLORS.accent})`,
            boxShadow: `0 20px 60px rgba(30,144,240,0.55), 0 0 ${40 + Math.sin(frame / 8) * 15}px rgba(47,212,230,0.4)`,
            fontFamily: FONT_TITLE,
            fontWeight: 800,
            fontSize: 52,
            color: '#fff',
          }}
        >
          <IconDownload size={48} color="#fff" stroke={2.6} />
          Descárgala ahora
          <div
            style={{
              position: 'absolute',
              top: 0,
              bottom: 0,
              width: 160,
              left: `${-30 + shimmer * 160}%`,
              background: 'linear-gradient(100deg, transparent, rgba(255,255,255,0.45), transparent)',
              transform: 'skewX(-20deg)',
            }}
          />
        </div>
        <Text size={40} style={{marginTop: 30, color: COLORS.text, fontWeight: 600, letterSpacing: 0.5}}>
          {DOMAIN}
        </Text>
        <Text size={24} style={{marginTop: 8}}>
          App para Android · Gratis
        </Text>
      </div>
    </AbsoluteFill>
  );
};
