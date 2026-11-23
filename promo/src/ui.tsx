import React from 'react';
import {
  AbsoluteFill,
  Img,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
  Easing,
} from 'remotion';
import {COLORS, FONT_TEXT, FONT_TITLE} from './config';

// ── animación ────────────────────────────────────────────────
export const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/** Spring 0→1 que arranca en `delay` frames. */
export const useIn = (delay = 0, damping = 18, mass = 0.9) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  return spring({frame: frame - delay, fps, config: {damping, mass, stiffness: 120}});
};

/** Progreso lineal suavizado entre dos frames. */
export const ease = (frame: number, a: number, b: number) =>
  interpolate(frame, [a, b], [0, 1], {...clamp, easing: Easing.bezier(0.22, 1, 0.36, 1)});

/** Estilo de entrada: sube, desenfoque → nítido. */
export const rise = (p: number, dist = 40): React.CSSProperties => ({
  opacity: p,
  transform: `translateY(${(1 - p) * dist}px)`,
  filter: `blur(${(1 - p) * 10}px)`,
});

// ── tipografía ───────────────────────────────────────────────
export const Title: React.FC<{size?: number; style?: React.CSSProperties; children: React.ReactNode}> = ({
  size = 96,
  style,
  children,
}) => (
  <div
    style={{
      fontFamily: FONT_TITLE,
      fontWeight: 800,
      fontSize: size,
      lineHeight: 1.02,
      letterSpacing: -size * 0.035,
      color: COLORS.text,
      ...style,
    }}
  >
    {children}
  </div>
);

export const Grad: React.FC<{children: React.ReactNode}> = ({children}) => (
  <span
    style={{
      background: `linear-gradient(100deg, ${COLORS.accent2}, ${COLORS.cyan})`,
      WebkitBackgroundClip: 'text',
      backgroundClip: 'text',
      color: 'transparent',
    }}
  >
    {children}
  </span>
);

export const Text: React.FC<{size?: number; style?: React.CSSProperties; children: React.ReactNode}> = ({
  size = 30,
  style,
  children,
}) => (
  <div style={{fontFamily: FONT_TEXT, fontSize: size, color: COLORS.muted, lineHeight: 1.4, ...style}}>
    {children}
  </div>
);

/** Palabras que entran una a una. */
export const Words: React.FC<{text: string; start: number; every?: number; style?: React.CSSProperties; accentFrom?: number}> = ({
  text,
  start,
  every = 4,
  style,
  accentFrom = Infinity,
}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const words = text.split(' ');
  return (
    <span style={style}>
      {words.map((w, i) => {
        const p = spring({frame: frame - start - i * every, fps, config: {damping: 16, stiffness: 140}});
        return (
          <span
            key={i}
            style={{
              display: 'inline-block',
              marginRight: '0.25em',
              opacity: p,
              transform: `translateY(${(1 - p) * 0.5}em) rotate(${(1 - p) * 4}deg)`,
              filter: `blur(${(1 - p) * 8}px)`,
            }}
          >
            {i >= accentFrom ? <Grad>{w}</Grad> : w}
          </span>
        );
      })}
    </span>
  );
};

// ── fondo ────────────────────────────────────────────────────
const wavePath = (w: number, h: number, amp: number, len: number, phase: number) => {
  let d = `M 0 ${h / 2}`;
  for (let x = 0; x <= w; x += 12) {
    const y = h / 2 + Math.sin((x / len) * Math.PI * 2 + phase) * amp * (0.6 + 0.4 * Math.sin(x / 700 + phase * 0.3));
    d += ` L ${x} ${y.toFixed(1)}`;
  }
  return d;
};

export const Background: React.FC = () => {
  const frame = useCurrentFrame();
  const {width, height} = useVideoConfig();
  const t = frame / 30;
  const blob = (x: number, y: number, r: number, c: string) => (
    <div
      style={{
        position: 'absolute',
        left: x - r,
        top: y - r,
        width: r * 2,
        height: r * 2,
        borderRadius: '50%',
        background: `radial-gradient(circle, ${c} 0%, transparent 65%)`,
      }}
    />
  );
  return (
    <AbsoluteFill style={{background: `radial-gradient(120% 90% at 50% 0%, #0f2232 0%, ${COLORS.bg} 45%, ${COLORS.bgDeep} 100%)`, overflow: 'hidden'}}>
      {blob(width * (0.2 + 0.06 * Math.sin(t * 0.4)), height * (0.25 + 0.05 * Math.cos(t * 0.3)), 620, 'rgba(30,144,240,0.28)')}
      {blob(width * (0.85 + 0.05 * Math.cos(t * 0.35)), height * (0.75 + 0.06 * Math.sin(t * 0.25)), 560, 'rgba(47,212,230,0.16)')}
      {blob(width * (0.6 + 0.08 * Math.sin(t * 0.2)), height * 0.1, 420, 'rgba(58,100,255,0.12)')}
      <svg width={width} height={height} style={{position: 'absolute', inset: 0, opacity: 0.55}}>
        <defs>
          <linearGradient id="wg" x1="0" x2="1">
            <stop offset="0" stopColor={COLORS.accent} stopOpacity="0" />
            <stop offset="0.5" stopColor={COLORS.cyan} stopOpacity="0.55" />
            <stop offset="1" stopColor={COLORS.accent} stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0, 1, 2, 3, 4].map((i) => (
          <path
            key={i}
            d={wavePath(width, height, 40 + i * 14, 900 + i * 180, t * (0.6 + i * 0.12) + i)}
            transform={`translate(0 ${(i - 2) * 46 + height * 0.18})`}
            stroke="url(#wg)"
            strokeWidth={1.4}
            fill="none"
            opacity={0.25 + i * 0.12}
          />
        ))}
      </svg>
      {/* viñeta */}
      <AbsoluteFill style={{background: 'radial-gradient(ellipse at center, transparent 55%, rgba(0,0,0,0.55) 100%)'}} />
    </AbsoluteFill>
  );
};

// ── piezas de UI ─────────────────────────────────────────────
export const Logo: React.FC<{size: number; glow?: number; style?: React.CSSProperties}> = ({size, glow = 1, style}) => (
  <Img
    src={staticFile('logo.png')}
    style={{
      width: size,
      height: size,
      filter: `drop-shadow(0 0 ${size * 0.25 * glow}px rgba(30,144,240,${0.55 * glow}))`,
      ...style,
    }}
  />
);

export const Glass: React.FC<{style?: React.CSSProperties; children?: React.ReactNode; glow?: number}> = ({
  style,
  children,
  glow = 0,
}) => (
  <div
    style={{
      background: `linear-gradient(160deg, rgba(255,255,255,0.09), rgba(255,255,255,0.025))`,
      border: `1.5px solid rgba(${glow > 0 ? '90,180,255' : '255,255,255'},${0.12 + glow * 0.5})`,
      borderRadius: 32,
      backdropFilter: 'blur(24px)',
      boxShadow: `0 30px 80px rgba(0,0,0,0.45), inset 0 1px 0 rgba(255,255,255,0.12), 0 0 ${60 * glow}px rgba(30,144,240,${0.45 * glow})`,
      ...style,
    }}
  >
    {children}
  </div>
);

/** Carátulas generadas (degradados de la marca). */
const COVERS = [
  ['#2fd4e6', '#1e90f0', '#0b3b70'],
  ['#ff7ab6', '#7a5cff', '#1b1a5a'],
  ['#ffd36e', '#ff7a59', '#5a1f3a'],
  ['#7cf2b0', '#1fb5a8', '#0b3d4a'],
  ['#b7a6ff', '#5f7dff', '#1a2560'],
  ['#ff9f6e', '#e8487a', '#3a1450'],
  ['#9be7ff', '#3a7bd5', '#0d2343'],
  ['#f6f1a8', '#5ed1a0', '#14485a'],
];
export const Cover: React.FC<{i: number; size: number; radius?: number; logo?: boolean; style?: React.CSSProperties}> = ({
  i,
  size,
  radius = 18,
  logo = false,
  style,
}) => {
  const [a, b, c] = COVERS[i % COVERS.length];
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        flexShrink: 0,
        background: `radial-gradient(circle at 25% 20%, ${a} 0%, transparent 55%), conic-gradient(from ${i * 47}deg at 60% 60%, ${b}, ${c}, ${a}, ${b})`,
        position: 'relative',
        overflow: 'hidden',
        ...style,
      }}
    >
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background: `repeating-radial-gradient(circle at ${30 + i * 7}% ${70 - i * 5}%, rgba(255,255,255,0.08) 0 2px, transparent 2px ${10 + i}px)`,
          mixBlendMode: 'overlay',
        }}
      />
      {logo && (
        <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
          <Logo size={size * 0.45} glow={0.6} />
        </div>
      )}
    </div>
  );
};

/** Marco de teléfono. */
export const Phone: React.FC<{width?: number; style?: React.CSSProperties; children?: React.ReactNode}> = ({
  width = 420,
  style,
  children,
}) => {
  const height = width * 2.08;
  return (
    <div
      style={{
        width,
        height,
        borderRadius: width * 0.13,
        padding: width * 0.03,
        background: 'linear-gradient(160deg, #2a3a48, #0d161e 40%, #1b2833)',
        boxShadow: '0 60px 140px rgba(0,0,0,0.6), 0 0 0 1.5px rgba(255,255,255,0.08), inset 0 0 0 2px rgba(255,255,255,0.05)',
        position: 'relative',
        ...style,
      }}
    >
      <div
        style={{
          position: 'relative',
          width: '100%',
          height: '100%',
          borderRadius: width * 0.105,
          overflow: 'hidden',
          background: COLORS.bg,
        }}
      >
        {children}
        <div
          style={{
            position: 'absolute',
            top: width * 0.03,
            left: '50%',
            transform: 'translateX(-50%)',
            width: width * 0.28,
            height: width * 0.075,
            borderRadius: 99,
            background: '#000',
            zIndex: 10,
          }}
        />
      </div>
    </div>
  );
};

export const Pill: React.FC<{children: React.ReactNode; style?: React.CSSProperties; active?: boolean}> = ({
  children,
  style,
  active,
}) => (
  <div
    style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 10,
      padding: '10px 20px',
      borderRadius: 99,
      fontFamily: FONT_TEXT,
      fontWeight: 600,
      fontSize: 22,
      color: active ? '#fff' : COLORS.text,
      background: active ? `linear-gradient(135deg, ${COLORS.accent2}, ${COLORS.accent})` : 'rgba(255,255,255,0.07)',
      border: `1.5px solid ${active ? 'rgba(255,255,255,0.25)' : 'rgba(255,255,255,0.12)'}`,
      boxShadow: active ? '0 10px 30px rgba(30,144,240,0.45)' : 'none',
      ...style,
    }}
  >
    {children}
  </div>
);

/** Barras de ecualizador animadas. */
export const EqBars: React.FC<{n?: number; w?: number; h?: number; color?: string; speed?: number}> = ({
  n = 4,
  w = 6,
  h = 26,
  color = COLORS.cyan,
  speed = 1,
}) => {
  const frame = useCurrentFrame();
  return (
    <div style={{display: 'flex', alignItems: 'flex-end', gap: w * 0.6, height: h}}>
      {Array.from({length: n}).map((_, i) => {
        const v = 0.3 + 0.7 * Math.abs(Math.sin(frame * 0.18 * speed + i * 1.7) * Math.cos(frame * 0.07 * speed + i));
        return <div key={i} style={{width: w, height: h * v, borderRadius: w, background: color}} />;
      })}
    </div>
  );
};

// ── iconos (SVG) ─────────────────────────────────────────────
type IconProps = {size?: number; color?: string; stroke?: number};
const Svg: React.FC<IconProps & {children: React.ReactNode}> = ({size = 32, color = 'currentColor', stroke = 2.2, children}) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
);
export const IconPlay = (p: IconProps) => (
  <svg width={p.size ?? 32} height={p.size ?? 32} viewBox="0 0 24 24" fill={p.color ?? 'currentColor'}>
    <path d="M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z" />
  </svg>
);
export const IconPause = (p: IconProps) => (
  <svg width={p.size ?? 32} height={p.size ?? 32} viewBox="0 0 24 24" fill={p.color ?? 'currentColor'}>
    <rect x="6" y="4" width="4" height="16" rx="1.5" />
    <rect x="14" y="4" width="4" height="16" rx="1.5" />
  </svg>
);
export const IconNext = (p: IconProps) => (
  <svg width={p.size ?? 32} height={p.size ?? 32} viewBox="0 0 24 24" fill={p.color ?? 'currentColor'}>
    <path d="M5 5.5v13a1 1 0 0 0 1.55.83L16 13v5a1 1 0 0 0 2 0V6a1 1 0 0 0-2 0v5L6.55 4.67A1 1 0 0 0 5 5.5z" />
  </svg>
);
export const IconPrev = (p: IconProps) => (
  <svg width={p.size ?? 32} height={p.size ?? 32} viewBox="0 0 24 24" fill={p.color ?? 'currentColor'} style={{transform: 'scaleX(-1)'}}>
    <path d="M5 5.5v13a1 1 0 0 0 1.55.83L16 13v5a1 1 0 0 0 2 0V6a1 1 0 0 0-2 0v5L6.55 4.67A1 1 0 0 0 5 5.5z" />
  </svg>
);
export const IconNoAds = (p: IconProps) => (
  <Svg {...p}>
    <rect x="3" y="6" width="18" height="12" rx="3" />
    <path d="M4 4l16 16" />
  </Svg>
);
export const IconSkip = (p: IconProps) => (
  <Svg {...p}>
    <path d="M5 5l7 7-7 7" />
    <path d="M13 5l7 7-7 7" />
  </Svg>
);
export const IconDownload = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 4v11" />
    <path d="M7 10l5 5 5-5" />
    <path d="M5 20h14" />
  </Svg>
);
export const IconEq = (p: IconProps) => (
  <Svg {...p}>
    <path d="M6 4v16M12 4v16M18 4v16" />
    <circle cx="6" cy="14" r="2.2" fill="#0b1720" />
    <circle cx="12" cy="8" r="2.2" fill="#0b1720" />
    <circle cx="18" cy="16" r="2.2" fill="#0b1720" />
  </Svg>
);
export const IconCheck = (p: IconProps) => (
  <Svg {...p}>
    <path d="M5 12.5l4.5 4.5L19 7.5" />
  </Svg>
);
export const IconUser = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
  </Svg>
);
export const IconSpark = (p: IconProps) => (
  <svg width={p.size ?? 32} height={p.size ?? 32} viewBox="0 0 24 24" fill={p.color ?? 'currentColor'}>
    <path d="M12 2l2.2 6.6L21 11l-6.8 2.4L12 20l-2.2-6.6L3 11l6.8-2.4z" />
  </svg>
);
export const IconWifiOff = (p: IconProps) => (
  <Svg {...p}>
    <path d="M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0" />
    <circle cx="12" cy="19" r="1" fill={p.color ?? 'currentColor'} />
    <path d="M3 3l18 18" />
  </Svg>
);
