'use strict';
const config = window.SITE_CONFIG || {};
function safeUrl(value) {
  if (typeof value !== 'string' || !value.trim()) return null;
  try {
    const url = new URL(value.trim(), document.baseURI);
    return ['https:', 'http:', 'file:'].includes(url.protocol) ? url : null;
  } catch { return null; }
}
const apk = safeUrl(config.apkUrl);
if (apk) {
  document.querySelectorAll('.download-link').forEach(link => {
    link.href = apk.href;
    link.removeAttribute('aria-disabled');
    link.removeAttribute('role');
    link.setAttribute('download', '');
  });
  document.querySelectorAll('[data-download-status]').forEach(node => {
    node.textContent = 'テスト版としてご利用ください。';
  });
}
function loadImage(src, alt, onLoad) {
  const url = safeUrl(src);
  if (!url) return;
  const image = new Image();
  image.alt = alt;
  image.decoding = 'async';
  image.onload = () => onLoad(image);
  // 未配置の画像はDOMへ追加せず、自然なプレースホルダーを維持。
  image.onerror = () => {};
  image.src = url.href;
}
loadImage(config.heroImage, config.heroAlt || 'ねことまいにちのアプリ画面', image => {
  document.querySelector('#hero-visual').replaceChildren(image);
});
const gallery = document.querySelector('#screenshot-gallery');
const shots = Array.isArray(config.screenshots) ? config.screenshots.slice(0, 4) : [];
if (shots.length >= 2) {
  while (gallery.children.length > shots.length) gallery.lastElementChild.remove();
  while (gallery.children.length < shots.length) {
    const figure = document.createElement('figure');
    const placeholder = document.createElement('div');
    placeholder.className = 'screen-placeholder';
    placeholder.textContent = '画像を準備しています';
    figure.append(placeholder, document.createElement('figcaption'));
    gallery.append(figure);
  }
  shots.forEach((shot, index) => {
    const figure = gallery.children[index];
    figure.querySelector('figcaption').textContent = shot.caption || '';
    loadImage(shot.src, shot.alt || 'ねことまいにちのアプリ画面', image => {
      image.className = 'screenshot';
      figure.firstElementChild.replaceWith(image);
    });
  });
}
