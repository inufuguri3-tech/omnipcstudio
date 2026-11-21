# OmniLive iOS (.ipa) ビルド & App Store 公開ガイド

## 1. Android版との違い（Apple iOS の仕様制限）
- **できること**:
  - AI Live チャット / **コード生成モード（1タップでPC保存・HTMLブラウザ起動）**
  - **PC操作エージェント**（Windows / Mac の OmniLive PC Studio を Wi-Fi 経由で遠隔操作）
- **iOSで制限されていること**:
  - iPhone (iOS) はセキュリティ上、他社のアプリ画面を読み取ったり自動タップしたりする **Accessibility API（スマホ他アプリ自動操作）を App Store アプリに許可していません**。そのためスマホ単体の他アプリ自動操作は Android 版専用です。

## 2. GitHub Actions で `.ipa` を自動生成する手順（Mac不要の事前確認ビルド）
1. このリポジトリを GitHub にプッシュすると、`.github/workflows/build-ios-ipa.yml` が `macos-latest` 上で自動的に動きます。
2. GitHub の **Actions** タブ → **Build iOS Companion IPA** → 最新の実行結果から **`OmniLiveIOS-unsigned-ipa`** をダウンロードできます。

> ※ GitHub Actions 上で生成されるのは Apple の配布証明書を含まない「未署名 (unsigned) `.ipa`」です。App Store への公開や実機インストールには署名が必要です。

## 3. App Store Connect へアップロードする手順（要 Mac / Xcode & Apple Developer Program）
1. [Apple Developer Program](https://developer.apple.com/)（年間 $99）に登録します。
2. Mac に XcodeGen を導入し、プロジェクトを生成します:
   ```bash
   brew install xcodegen
   cd ios_app
   xcodegen generate
   open OmniLiveIOS.xcodeproj
   ```
3. Xcode の **Signing & Capabilities** で `Team` にご自身の Apple Developer アカウントを設定し、`Bundle Identifier` を固有の名前に変更します。
4. メニュー **Product → Archive** を実行し、Organizer から **Distribute App → App Store Connect** を選択してアップロードします。
5. App Store Connect 上でスクリーンショット・プライバシー表記を登録し、審査提出すると iPhone App Store に公開できます。
