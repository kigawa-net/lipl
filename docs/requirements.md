# Lipl Requirements

> 関連ドキュメント: [README.md](../README.md) | [CLAUDE.md](../CLAUDE.md)

## Vision

飲食店向けの公式ページ作成サービス。

キャッチコピー:

お店の情報を入力するだけ。3分で公式ページを公開。

## MVP Flow

```
会員登録
  ↓
店舗作成（店名・業種・所在地）
  ↓
フォーム入力（営業時間・電話番号・SNSリンク・写真・メニュー）
  ↓
AIヒアリング（チャット形式でお店の魅力・こだわりを引き出す）
  ↓
LP生成（AIがテキスト・レイアウトを自動生成）
  ↓
プレビュー（スマホ・PCで確認）
  ↓
公開（<slug>-lipl.kigawa.net で即時公開）
```

## 認証

- **Keycloak** を使用（`user.kigawa.net` で運用中のインスタンス）
- realm: `lipl`（新規作成）
- フロントエンドは OIDC (Authorization Code Flow + PKCE)
- バックエンドは JWT 検証（Keycloak の公開鍵で署名検証）
- パスワード管理・メール認証・パスワードリセットはすべて Keycloak に委譲

### 画面

| 画面 | パス | 説明 |
|------|------|------|
| ログイン | `/login` | Keycloak のログインページへリダイレクト |
| コールバック | `/callback` | Keycloak からの認可コードを受け取りトークン取得 |
| ログアウト | `/logout` | Keycloak セッションも終了 |

## 画面一覧

### ダッシュボード（ログイン後）

| 画面 | パス | 説明 |
|------|------|------|
| ホーム | `/dashboard` | 店舗一覧・公開状態・アクセス数サマリ |
| 店舗作成 | `/dashboard/stores/new` | 店舗の基本情報入力 |
| 店舗編集 | `/dashboard/stores/:id/edit` | フォーム入力・写真・メニュー管理 |
| AIヒアリング | `/dashboard/stores/:id/interview` | チャット形式のAIヒアリング |
| LP編集 | `/dashboard/stores/:id/lp` | 生成結果の確認・再生成・手動調整 |
| QRコード | `/dashboard/stores/:id/qr` | QRコード生成・ダウンロード |
| お知らせ投稿 | `/dashboard/stores/:id/news` | お知らせの作成・管理（Basic以上） |
| アクセス解析 | `/dashboard/stores/:id/analytics` | PV・流入元（Basic以上） |
| プラン管理 | `/dashboard/billing` | Stripeポータルへのリンク |

### 公開LP

| 画面 | URL | 説明 |
|------|-----|------|
| 店舗LP（デフォルト） | `https://<slug>-lipl.kigawa.net/` | 全プラン共通 |
| 店舗LP（独自ドメイン） | `https://<独自ドメイン>/` | Pro プランのみ |

### ランディングページ（未ログイン）

| 画面 | パス | 説明 |
|------|------|------|
| トップ | `/` | サービス紹介・料金・CTA |
| 料金 | `/pricing` | プラン比較表 |

## ネットワーク

- **店舗LPデフォルトドメイン**: `<slug>-lipl.kigawa.net`
  - slug は店舗作成時にユーザーが設定（英数字・ハイフンのみ、一意）
  - ワイルドカード DNS `*.lipl.kigawa.net` で一括解決
  - Ingress は slug をホスト名によりルーティング
- **独自ドメイン** (Pro): ユーザーが DNS を `lipl.kigawa.net` に CNAME → Ingress で `customDomain` をルーティング
- **サービス自体**: `lipl.kigawa.net`（ダッシュボード・LP生成API等）

## AIヒアリングフロー

1. システムが店舗の基本情報（業種・所在地など）を読み込む
2. AIが3〜5問のオープンな質問をチャット形式で行う
   - 例: 「お店のこだわりを一言で表すと？」
   - 例: 「どんなお客様に来てほしいですか？」
   - 例: 「他のお店と違うと思うところは？」
3. 回答をもとにAIがLP用のキャッチコピー・説明文を生成
4. Free プランは質問3回・生成1回のみ。Basic以上は無制限で再生成可能

## データモデル

実装コードを正とする。最新のスキーマはバックエンドの DB マイグレーションファイルを参照すること。

## Tech Stack

### Frontend

- React
- React Router v8
- TypeScript
- Tailwind CSS

### Backend

- Kotlin
- Ktor
- MariaDB

### Infrastructure

- Kubernetes
- Docker
- GitHub Actions

### External Services

- **Keycloak**（認証・認可。`user.kigawa.net` で運用中）
- Stripe（決済・サブスクリプション管理）
- Claude Haiku（Free プランのAI生成）
- Claude Sonnet（Basic・Pro プランのAI生成）
- オブジェクトストレージ（写真アップロード）

## Pricing

### Free（無料）

- 店舗数 1
- LP 1ページ
- 写真 5枚
- メニュー 5品
- AI生成 1回
- AI質問 3回
- **QRコード生成**
- Liplロゴ表示
- 独自ドメイン不可（`<slug>-lipl.kigawa.net` 固定）
- 使用モデル: Claude Haiku

CTA: 無料でお店のページとQRコードを作成

### Basic（月980円）

- AI再生成（何度でも）
- 写真 20枚
- メニュー 30品
- Liplロゴ非表示
- お知らせ投稿
- アクセス解析（基本）
- 使用モデル: Claude Sonnet

### Pro（月2,980円）

- **独自ドメイン**（CNAME で `lipl.kigawa.net` に設定）
- 問い合わせフォーム
- SEO設定
- 多言語対応
- 予約リンク複数
- 高度なアクセス解析
- 使用モデル: Claude Sonnet

### 料金設計の方針

- QRコードは Free に含める（初回体験として強力。印刷→店頭掲示の導線を無料で提供）
- 課金ポイントは独自ドメイン・ロゴ非表示・AI再生成・SEO・多言語・アクセス解析に集中させる
