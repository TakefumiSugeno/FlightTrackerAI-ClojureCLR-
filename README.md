# FlightTrackerAI 🛫

Google Flights と Skyscanner を自動巡回し、最安値・航空券価格推移・旅程詳細（フライト時間・乗継時間）を常時監視する、仕様書駆動開発（SDD）準拠の ClojureCLR (.NET 10) 製フルスタック Web アプリケーションです。

---

## 🌟 主な機能

1. **AI 構造化文書・自然言語解析アシスタント (OpenRouter 連携)**
   - 箇条書き・YAML・自然文プロンプトから旅行条件（出発地・目的地・日程・予算・乗継・希望航空会社）を自動抽出し、新規登録フォームへワンクリック展開。
2. **2大フライトプロバイダー自動巡回 & スクレイピング (Playwright for .NET)**
   - Google Flights（エメラルドグリーン）および Skyscanner（スカイブルー）を定期巡回。
   - レートリミット回避・Bot検知回避（Stealth / Anti-detection ブラウザコンテキスト）を標準装備。
3. **最安値推移チャート & 複数社旅程タイムライン**
   - Chart.js による価格変動グラフ（目標アラート価格ライン付き）。
   - 各区間の航空会社・便名・フライト時間・乗継空港・待ち時間を完全図示。
4. **Excel風コンパクト一覧表示 & カード表示の即時切替**
   - 10項目を横スクロールなしで100%幅に収める2段スタックテーブル。
   - ステータスタブ（すべて/監視中/一時停止/完了・エラー）およびキーワード絞り込み。
5. **Discord / Slack Webhook 自動通知**
   - 目標価格達成時や最安値更新時に、リッチな Embed 形式で即時アラート通知。
6. **堅牢なモーダルナビゲーション & UX保護**
   - ブラウザ「戻る」ボタン（History API）完全連動、ESCキー/背景クリックによる直感的な親画面復帰。
   - バリデーションエラー時の入力データ維持＆インラインエラー表示、IME日本語変換保護。
7. **MVP 向けリアルタイム観測 & ログ共有基盤**
   - 全コンポーネントの実行ログを `doc/work/app.log` に自動記録。
   - UIヘッダーの「ログ確認」ボタンから最新ログの閲覧・ワンクリックコピー（AI共有用）が可能。

---

## 🏗️ システム構成・アーキテクチャ

- **言語・ランタイム**: ClojureCLR 1.12.2 (.NET 10 / `net10.0`)
- **Web & SSR**: Hiccup風 純粋関数 HTML DSL (`html_dsl.clj`) + `System.Net.HttpListener` + HTMX + Tailwind CSS
- **永続化**: SQLite (WAL モード / 外部キー制約有効) + `Microsoft.Data.Sqlite`
- **スクレイピング**: Microsoft Playwright for .NET (Chromium 自動プロビジョニング、非同期 Task 解決ユーティリティ)
- **AI 解析**: OpenRouter REST API (`google/gemini-2.0-flash-001` / `anthropic/claude-3.5-sonnet` 等)
- **テストフレームワーク**: ClojureCLR 標準 `clojure.test` + カスタムテストランナー (`test_runner.clj`)（合否一覧 & カバレッジレポート HTML 自動生成）

---

## 🚀 クイックスタート

### 前提条件

- [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0)
- PowerShell (pwsh)

### 1. 起動方法

#### 方法 A: VS Code で起動 (推奨)

1. VS Code で本リポジトリを開きます。
2. **「実行とデバッグ (Run & Debug)」(Ctrl+Shift+D)** を開きます。
3. **`.NET: Web App (Internal Browser)`** を選択して **F5** を押します。
4. 自動でビルド ➔ ブラウザバイナリ確認 ➔ サーバー起動 ➔ VS Code 内蔵ブラウザ（Simple Browser）にて `http://localhost:5121` が表示されます。

#### 方法 B: CLI で起動

```bash
dotnet run --project src/FlightTrackerAI.Web
```

ブラウザで `http://localhost:5121` にアクセスします。

---

## ⚙️ 環境設定 (.env / Web UI)

### 方法 1: Web UI から設定 (簡単・推奨)

画面右上の **歯車アイコン ⚙「全体設定」** をクリックし、OpenRouter API キーや Webhook URL を入力して「設定を保存」します（SQLite DB に永続化されます）。

### 方法 2: `.env` ファイルで設定

プロジェクトルートに `.env` ファイルを作成します（[.env.example](.env.example) 参照）:

```env
OPENROUTER_API_KEY=sk-or-v1-your-openrouter-api-key-here
# OPENROUTER_MODEL=google/gemini-2.0-flash-001
```

---

## 🧪 テスト実行と視覚レポート

本プロジェクトは **仕様書駆動開発 (SDD)** のテスト出力規約に準拠しており、全テストケースの合否一覧およびコードカバレッジを HTML レポートとして出力します。

```powershell
./scripts/test.ps1
```

実行後、以下の2つの HTML レポートが自動生成されます:

1. **テストケース合否レポート (OK/NG一覧)**:
   - [doc/work/TestResults/TestResults.html](doc/work/TestResults/TestResults.html) (22スイート、451アサーション全件合格)
2. **コードカバレッジレポート (網羅率%)**:
   - [doc/work/CoverageReport/index.html](doc/work/CoverageReport/index.html) (カバレッジ 94.9% 達成)

---

## 📁 ディレクトリ構造

```
FlightTrackerAI-ClojureCLR-/
├── deps.edn                           # Clojure CLI 依存・クラスパス定義
├── dotnet-tools.json                  # .NET ローカルツール (clojure.cljr, clojure.main)
├── FlightTrackerAI.slnx               # .NET 10 ソリューション
├── src/
│   ├── FlightTrackerAI.Core/          # 純粋ドメイン型・不変バリデーション・価格分析
│   │   └── flight_tracker_ai/core/
│   │       ├── domain.clj
│   │       ├── validation.clj
│   │       ├── analysis.clj
│   │       └── dto.clj
│   ├── FlightTrackerAI.Infrastructure/# SQLite・Playwright・OpenRouter・Webhook・ワーカー
│   │   └── flight_tracker_ai/infrastructure/
│   │       ├── database.clj
│   │       ├── task_repository.clj
│   │       ├── flight_repository.clj
│   │       ├── settings_repository.clj
│   │       ├── scraper_common.clj
│   │       ├── google_flights_scraper.clj
│   │       ├── skyscanner_scraper.clj
│   │       ├── scraping_worker.clj
│   │       ├── ai_client.clj
│   │       ├── notification.clj
│   │       └── app_logger.clj
│   └── FlightTrackerAI.Web/           # Web UI・サーバー・ビュー
│       ├── Program.fs                 # .NET 10 起動ブートストラップ
│       └── flight_tracker_ai/web/
│           ├── server.clj             # System.Net.HttpListener Webサーバー
│           ├── controllers/api_controller.clj
│           └── views/
│               ├── html_dsl.clj       # Hiccup風 HTML DSL
│               ├── layout.clj
│               ├── dashboard.clj
│               └── modals.clj
├── test/
│   ├── test_runner.clj                # Clojure製 テストランナー & HTMLレポート生成
│   ├── FlightTrackerAI.Core.Tests/
│   ├── FlightTrackerAI.Infrastructure.Tests/
│   └── FlightTrackerAI.Web.Tests/
├── doc/
│   ├── spec.md                         # システム仕様書
│   ├── design_detail.md                # 詳細設計書
│   ├── mock/index.html                 # UIモック原典
│   └── tasks/                          # SDD タスク計画 & レビュー記録
├── scripts/
│   ├── test.ps1                        # 全テスト実行 & HTMLレポート生成スクリプト
│   └── install-browsers.ps1            # Playwright ブラウザインストーラー
```

---

## 📜 ライセンス

MIT License
