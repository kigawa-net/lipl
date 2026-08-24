# lipl-api

Kotlin + Ktor バックエンド。詳細は [../docs/requirements.md](../docs/requirements.md) / [../docs/infrastructure.md](../docs/infrastructure.md) を参照。

## 開発

```bash
./gradlew run
```

デフォルトで `:8080` で起動する（`PORT` 環境変数で変更可）。

## ビルド・テスト

```bash
./gradlew build
```

## Docker

```bash
docker build -t lipl-api .
docker run -p 8080:8080 lipl-api
```

## ヘルスチェック

`GET /health` — `200 ok` を返す。
