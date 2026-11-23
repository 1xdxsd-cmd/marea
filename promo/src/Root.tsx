import React from 'react';
import {Composition} from 'remotion';
import {FPS, sceneStarts} from './config';
import {Promo} from './Promo';

export const RemotionRoot: React.FC = () => (
  <Composition id="MareaPromo" component={Promo} durationInFrames={sceneStarts().total} fps={FPS} width={1920} height={1080} />
);
