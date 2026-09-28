# MControl for iOS

iPhone版のMControlです。Android版とは送信方式が異なります。

## 送信方式

iPhoneのアプリは、BLEの広播でManufacturer Data（製造者データ）を自由に設定できません。
そのため、16bitのService UUIDを13個並べて命令を送っています。

並び順が動作の条件です。5番目と6番目に命令のUUIDを入れます。

| 位置 | 内容 |
|---|---|
| 1〜4 | 08F9 / 2349 / CBAE / D1C1（固定） |
| 5〜6 | パターンごとに変わる（下の表） |
| 7〜13 | 0D0C / 0F0E / 1110 / 1312 / 1514 / 1716 / 1918（固定） |

| パターン | 5番目 | 6番目 |
|---|---|---|
| 1 | 156F | 0B2C |
| 2 | 8E6C | 0B1E |
| 3 | 076D | 0B0F |
| 4 | B86A | 0B7B |
| 5 | 316B | 0B6A |
| 6 | AA68 | 0B58 |
| 7 | 2369 | 0B49 |
| 8 | D466 | 0BB1 |
| 9 | 5D67 | 0BA0 |
| 停止 | 9C6E | 0B3D |

## ビルド時の設定

ソースコード以外に、Xcodeで次の設定が必要です。

- Minimum Deployments：iOS 17.0
- Build Settings：`INFOPLIST_KEY_NSBluetoothAlwaysUsageDescription`（Debug・Release両方に説明文を入れる）
- Build Settings（User-Defined）：`ASSETCATALOG_COMPILER_APPICON_NAME` = `AppIcon`
  （これがないとアイコンがアプリに入らず、仮のアイコンが表示されます）
- Signing & Capabilities：Background Modes → Acts as a Bluetooth LE accessory
  （追加しましたが、アプリを開いたままで動いたため、必要かは未確認です）

## 動作確認環境

- Xcode 27
- iPhone 13 mini（iOS 26.6.2）
- アプリが前面にある状態で、パターン1〜9と停止の動作を確認

## 注意

- 伸縮・振動の2チャンネル独立制御は未対応です
- 無料のApple IDで入れた場合、7日ごとにXcodeから入れ直す必要があります
