import {bundle} from '@remotion/bundler';
import {renderStill, selectComposition} from '@remotion/renderer';
import path from 'node:path';
const frames = process.argv.slice(2).map(Number);
const serveUrl = await bundle({entryPoint: path.resolve('src/index.ts')});
const browserExecutable = process.env.REMOTION_BROWSER;
const composition = await selectComposition({serveUrl, id: 'MareaPromo', browserExecutable});
for (const frame of frames) {
  await renderStill({composition, serveUrl, frame, output: `out/stills/f${frame}.jpg`, imageFormat: 'jpeg', scale: 0.5, browserExecutable});
  console.log('ok', frame);
}
