# Lipl Requirements

## Vision

飲食店向けの公式ページ作成サービス。

キャッチコピー:

お店の情報を入力するだけ。3分で公式ページを公開。

## MVP Flow

会員登録 -> 店舗作成 -> フォーム入力 -> AIヒアリング -> LP生成 -> プレビュー -> 公開

## Tech Stack

### Frontend

- React
- React Router v8
- TypeScript
- Tailwind CSS

### Backend

- Kotlin
- Ktor
- PostgreSQL

### Infrastructure

- Kubernetes
- Docker
- GitHub Actions

### External Services

- Stripe
- Claude Haiku
- Claude Sonnet

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
- 独自ドメイン不可
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

- 独自ドメイン
- 問い合わせフォーム
- SEO設定
- 多言語対応
- 予約リンク複数
- 高度なアクセス解析
- 使用モデル: Claude Sonnet

### 料金設計の方針

- QRコードは Free に含める（初回体験として強力。印刷→店頭掲示の導線を無料で提供）
- 課金ポイントは独自ドメイン・ロゴ非表示・AI再生成・SEO・多言語・アクセス解析に集中させる
