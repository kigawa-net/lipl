# CLAUDE.md

このファイルは Claude Code (claude.ai/code) がリポジトリで作業する際のガイダンスを提供します。

## 基本規約

- 日本語で話す。
- GitHub の操作（issue・PR の作成・閲覧・コメント・レビューなど）は GitHub MCP ツール（`mcp__github__*`）で行う。
- PR はタイトル・本文・レビューコメントを日本語で作成する。コマンド名、ファイルパス、識別子、固有名詞は原文のまま記載してよい。

## 作業フロー

```
issue 作成 → ブランチ作成 → 実装 → PR 作成 → レビュー → マージ
```

- 直接 `main` にコミットしない。
- ブランチ名は `<type>/<short-description>` 形式（例: `feat/store-form`, `fix/ai-generation`）。
- PR body には変更内容・影響範囲・動作確認方法を日本語で記載する。
- `Closes #N` を PR body に記載し、マージ時に issue を自動クローズする。

## リポジトリ構成

```
lipl/
├── docs/          # 要件・設計ドキュメント
├── frontend/      # React + TypeScript フロントエンド（予定）
└── backend/       # Kotlin + Ktor バックエンド（予定）
```

## フロントエンド規約

- **言語**: TypeScript（`any` 禁止、型を必ず定義する）
- **フレームワーク**: React + React Router v8
- **スタイル**: Tailwind CSS（インラインスタイル禁止）
- **コンポーネント**: 関数コンポーネントのみ（クラスコンポーネント禁止）
- **状態管理**: React 標準フック（`useState`, `useReducer`）を優先し、必要に応じてライブラリを追加
- **ファイル名**: コンポーネントは PascalCase（例: `StoreForm.tsx`）、それ以外は camelCase

## バックエンド規約

- **言語**: Kotlin
- **フレームワーク**: Ktor
- **データベース**: MariaDB（マイグレーションファイルをスキーマの正とする）
- **パッケージ構成**: 機能単位でパッケージを切る（例: `store`, `auth`, `ai`）
- **API**: RESTful、レスポンスは JSON
- **認証**: JWT

## コミットメッセージ規約

`<type>: <概要>` 形式（英語）。

| type | 用途 |
|------|------|
| `feat` | 新機能 |
| `fix` | バグ修正 |
| `docs` | ドキュメント変更 |
| `refactor` | リファクタリング |
| `test` | テスト追加・修正 |
| `chore` | ビルド・設定変更 |

## データモデル

実装コードを正とする。最新のスキーマはバックエンドの DB マイグレーションファイルを参照する。

## 関連ドキュメント

- [README.md](README.md) — プロジェクト概要・技術構成・プラン
- [docs/requirements.md](docs/requirements.md) — 要件定義（MVP・画面・料金・技術スタック）
