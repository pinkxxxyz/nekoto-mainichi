# Korokke Life

黒猫と暮らす、縦画面のシンプルなTODO・お買い物メモAndroidアプリです。

## 開く

Android Studioでこのフォルダを開き、JDK 17 / Android SDK 35を設定して `app` を実行してください。

## 構成

- `MainActivity.kt`: Compose UI、各ダイアログ、チュートリアル
- `MainViewModel.kt`: Room操作
- `data/`: Roomエンティティ、DAO、並び順・期限ロジック
- `cat/`: UIから分離した猫の状態遷移
