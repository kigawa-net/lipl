# Lipl

> お店の情報を入力するだけ。
> 3分で公式ページを公開。

Liplは飲食店向けの公式ページ作成サービスです。

HTML、CSS、サーバー、ドメイン、DNSの知識がなくても、店舗情報・写真・メニューを入力するだけで公式ページを作成・公開できます。

## ドキュメント

| ファイル | 内容 |
|----------|------|
| [docs/requirements.md](docs/requirements.md) | 要件定義（MVP・画面・料金・技術スタック） |
| [CLAUDE.md](CLAUDE.md) | 開発規約（ブランチ・コミット・コーディング規約） |

## コンセプト

```text
店舗情報入力
↓
AI補助
↓
公式ページ生成
↓
公開
```

Instagramしか持っていない飲食店でも、誰でも簡単に公式ページを持てる世界を目指します。

## MVP機能

- 店舗情報登録
- 写真アップロード
- メニュー登録
- AIヒアリング
- LP自動生成
- プレビュー
- ワンクリック公開
- 公開URL発行
- 編集・公開停止

## ターゲット

- 個人経営飲食店
- カフェ
- 居酒屋
- ラーメン店
- レストラン
- キッチンカー

## プラン

### Free

- 店舗数1
- LP1ページ
- 写真5枚（フルHD）
- メニュー5品
- AI生成1回
- AI質問3回
- **QRコード生成**
- Liplロゴ表示
- 独自ドメイン不可
- Claude Haiku

### Basic（月980円）

- AI再生成（何度でも）
- AI質問5回
- 写真20枚（4K）
- メニュー30品
- ロゴ非表示
- お知らせ投稿
- Claude Sonnet

### Pro（月2,980円）

- 独自ドメイン
- お問い合わせフォーム
- SEO設定
- アクセス解析
- 多言語対応
- 予約リンク複数

## 技術構成

### フロントエンド

- React
- React Router v8
- TypeScript
- Tailwind CSS

### バックエンド

- Kotlin
- Ktor
- MariaDB
- S3互換オブジェクトストレージ

### インフラ

- Kubernetes
- Docker
- Ingress Controller
- cert-manager
- GitHub Actions

### 決済・AI

- Stripe
- Anthropic Claude Haiku
- Anthropic Claude Sonnet

## 想定構成

```text
ユーザー
↓
Ingress
↓
React Router v8 フロントエンド
↓
Ktor API
├─ MariaDB
├─ オブジェクトストレージ
├─ Stripe
└─ Claude API
```

## ビジョン

Liplは単なるLP作成ツールではありません。

飲食店の営業時間、メニュー、アクセス、予約、SNSをまとめる

**飲食店のデジタル名刺**

を目指します。
