import React, {useEffect, useState} from 'react';
import {AbsoluteFill, Audio, Sequence, continueRender, delayRender, interpolate, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
import {
  COLORS,
  FONT_TEXT,
  FPS,
  MUSIC_FILE,
  MUSIC_VOLUME,
  MUSIC_VOLUME_UNDER_VOICE,
  SCENES,
  SHOW_CAPTIONS,
  TRANSITION,
  VOICE_FILE,
  VOICE_START,
  VOICE_VOLUME,
  sceneFrames,
  sceneStarts,
} from './config';
import {CtaScene, DescubreScene, DisenoScene, IntroScene, PlanesScene, PremiumScene} from './scenes';
import {Background, clamp} from './ui';

const SCENE_COMPONENTS = [IntroScene, PremiumScene, PlanesScene, DescubreScene, DisenoScene, CtaScene];

// ── fuentes locales ─────────────────────────────────────────
const FONTS: [string, number, string][] = [
  ['Bricolage Grotesque', 500, 'bricolage-500'],
  ['Bricolage Grotesque', 600, 'bricolage-600'],
  ['Bricolage Grotesque', 700, 'bricolage-700'],
  ['Bricolage Grotesque', 800, 'bricolage-800'],
  ['Figtree', 400, 'figtree-400'],
  ['Figtree', 500, 'figtree-500'],
  ['Figtree', 600, 'figtree-600'],
  ['Figtree', 700, 'figtree-700'],
];
const useFonts = () => {
  const [handle] = useState(() => delayRender('fuentes'));
  useEffect(() => {
    Promise.all(
      FONTS.map(([family, weight, file]) =>
        new FontFace(family, `url(${staticFile(`fonts/${file}.woff2`)})`, {weight: String(weight)}).load().then((f) => (document.fonts as any).add(f)),
      ),
    )
      .then(() => continueRender(handle))
      .catch((e) => {
        console.error(e);
        continueRender(handle);
      });
  }, [handle]);
};

/** Transición entre escenas: entra nítida desde blur/zoom, sale hacia atrás. */
const SceneWrap: React.FC<{children: React.ReactNode; hasExit: boolean; dur: number}> = ({children, hasExit, dur}) => {
  const frame = useCurrentFrame();
  const inP = interpolate(frame, [0, TRANSITION], [0, 1], clamp);
  const outP = hasExit ? interpolate(frame, [dur - TRANSITION, dur], [0, 1], clamp) : 0;
  const scale = 1 + (1 - inP) * 0.06 - outP * 0.05;
  return (
    <AbsoluteFill
      style={{
        opacity: inP * (1 - outP),
        transform: `scale(${scale})`,
        filter: `blur(${(1 - inP) * 14 + outP * 14}px)`,
      }}
    >
      {children}
    </AbsoluteFill>
  );
};

const Captions: React.FC = () => {
  const frame = useCurrentFrame();
  const {starts, total} = sceneStarts();
  const cues: {from: number; to: number; text: string}[] = [];
  SCENES.forEach((s, si) => {
    s.captions.forEach(([at, text], ci) => {
      const from = starts[si] + Math.round(at * FPS);
      const next = ci + 1 < s.captions.length ? starts[si] + Math.round(s.captions[ci + 1][0] * FPS) : si + 1 < SCENES.length ? starts[si + 1] + Math.round(SCENES[si + 1].captions[0][0] * FPS) : total;
      cues.push({from, to: next, text});
    });
  });
  const cue = cues.find((c) => frame >= c.from && frame < c.to);
  if (!cue) return null;
  const p = interpolate(frame, [cue.from, cue.from + 6, cue.to - 4, cue.to], [0, 1, 1, 0], clamp);
  return (
    <div style={{position: 'absolute', bottom: 46, width: '100%', display: 'flex', justifyContent: 'center', zIndex: 100}}>
      <div
        style={{
          maxWidth: 1500,
          padding: '14px 30px',
          borderRadius: 18,
          background: 'rgba(5,13,21,0.62)',
          border: '1px solid rgba(255,255,255,0.08)',
          backdropFilter: 'blur(12px)',
          fontFamily: FONT_TEXT,
          fontWeight: 600,
          fontSize: 34,
          color: COLORS.text,
          textAlign: 'center',
          opacity: p,
          transform: `translateY(${(1 - p) * 10}px)`,
        }}
      >
        {cue.text}
      </div>
    </div>
  );
};

export const Promo: React.FC = () => {
  useFonts();
  const {durationInFrames} = useVideoConfig();
  const {starts} = sceneStarts();
  return (
    <AbsoluteFill style={{background: COLORS.bg}}>
      <Background />
      {SCENES.map((s, i) => {
        const Comp = SCENE_COMPONENTS[i];
        const last = i === SCENES.length - 1;
        const dur = sceneFrames(s) + (last ? 0 : TRANSITION);
        return (
          <Sequence key={s.id} from={starts[i]} durationInFrames={dur} name={s.id}>
            <SceneWrap hasExit={!last} dur={dur}>
              <Comp scene={s} />
            </SceneWrap>
          </Sequence>
        );
      })}
      {SHOW_CAPTIONS && <Captions />}
      {VOICE_FILE && (
        <Sequence from={Math.round(VOICE_START * FPS)}>
          <Audio src={staticFile(VOICE_FILE)} volume={VOICE_VOLUME} />
        </Sequence>
      )}
      {MUSIC_FILE && (
        <Audio
          src={staticFile(MUSIC_FILE)}
          volume={(f) =>
            interpolate(f, [0, 20, durationInFrames - 45, durationInFrames], [0, VOICE_FILE ? MUSIC_VOLUME_UNDER_VOICE : MUSIC_VOLUME, VOICE_FILE ? MUSIC_VOLUME_UNDER_VOICE : MUSIC_VOLUME, 0], clamp)
          }
        />
      )}
    </AbsoluteFill>
  );
};
