// 配布情報の変更はこのファイルだけで行えます。
window.SITE_CONFIG = {
  version: '0.1.0 beta',
  // TODO: 配布する実際のAPKの相対パス、または https:// URLを設定。
  // 空のままなら、トップのダウンロードボタンは無効になります。
  apkUrl: 'https://github.com/pinkxxxyz/nekoto-mainichi/releases/download/v1.0.0/nekoto-mainichi-android-v1.0.apk',
  // 画像を配置したらパスを設定。空の場合はCSSの部屋を表示します。
  heroImage: '',
  heroAlt: 'ねことまいにちのアプリ画面',
  // 以下の名前で assets/screenshots/ に置けば自動で表示されます。
  // ファイルがない場合はプレースホルダーを残します。
  screenshots: [
    { src: 'assets/screenshots/home.png', alt: '猫のいる部屋のアプリ画面', caption: '猫との暮らし' },
    { src: 'assets/screenshots/tasks.png', alt: 'やることとリマインダーのアプリ画面', caption: 'やること' },
    { src: 'assets/screenshots/shopping.png', alt: '買い物メモのアプリ画面', caption: '買い物' }
  ]
};
