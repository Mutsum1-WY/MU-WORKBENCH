/* 把网页版（唯一的源文件）同步进 App 的 www 目录。
   以后改功能只改 D:\交互设计\MU-WORKBENCH\index.html，然后跑一次 `npm run android:sync` 即可。 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const src = path.join(__dirname, '..', 'MU-WORKBENCH', 'index.html');
const dstDir = path.join(__dirname, 'www');
const dst = path.join(dstDir, 'index.html');

if (!fs.existsSync(src)) {
  console.error('[X] 找不到网页源文件: ' + src);
  process.exit(1);
}
fs.mkdirSync(dstDir, { recursive: true });
fs.copyFileSync(src, dst);

const h = p => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex').slice(0, 12);
const same = h(src) === h(dst);
console.log('[OK] 已同步 ' + path.basename(src) + ' -> www/index.html');
console.log('     源文件 ' + (fs.statSync(src).size / 1024).toFixed(1) + ' KB  sha256:' + h(src));
console.log('     拷贝   ' + (fs.statSync(dst).size / 1024).toFixed(1) + ' KB  sha256:' + h(dst) + (same ? '  (一致)' : '  (不一致!)'));
process.exit(same ? 0 : 1);
