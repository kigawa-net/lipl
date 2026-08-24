# Lipl Infrastructure Design

> 関連ドキュメント: [requirements.md](requirements.md) | [../README.md](../README.md) | [../CLAUDE.md](../CLAUDE.md)

このドキュメントは `docs/requirements.md` の要件を、既存の kigawa-net 基盤（`kigawa-net/platform` で ArgoCD GitOps 管理されているクラスタ）の上にどう実装するかを定義する。

> **注記**: `kigawa-net/platform` は本ドキュメント作成時点ではまだ存在しないリポジトリ名。調査は現行の [kigawa-net-k8s](https://github.com/kigawa-net/kigawa-net-k8s) リポジトリ（同様にArgoCD GitOpsでこのクラスタを管理している）に対して行った。`platform` は `kigawa-net-k8s` を指す想定名として本ドキュメント内で使用している（リネームまたは移行が前提）。

## 前提（既存インフラの調査結果）

`platform`（調査時点では `kigawa-net-k8s`）リポジトリの既存アプリ（keruta, lp 等）を調査し、以下の規約を確認した。Lipl もこれに従う。

| 項目 | 既存の規約 |
|------|-----------|
| GitOpsリポジトリ | `platform`（`main` へのマージで自動デプロイ） |
| マニフェスト配置 | app本体のリポジトリ（`lipl`）には k8s マニフェストは置かず、`platform` 側の `lipl/main/` に配置する |
| ArgoCD Application | `apps/lipl-main.yml` を新設し、`kigawa-net` AppProject 配下に追加 |
| Namespace命名 | `kigawa-net-lipl-main`（`kigawa-net-keruta-main` 等と同様） |
| Ingress Class | `haproxy`（nginx ではない） |
| ベースドメイン | `kigawa.net`。個々のアプリは `<app>.kigawa.net` の1ラベルサブドメインを使用し、Ingress マニフェスト自体に TLS ブロックの記載はない（クラスタ側で `kigawa.net` 系サブドメインを一括カバーするデフォルト証明書が設定されていると推測される） |
| コンテナレジストリ | `harbor.kigawa.net`（`private/<service>` パス。`imagePullSecrets: harbor-registry`） |
| イメージタグ | 本番 `main-<commit-sha>`（`imagePullPolicy: IfNotPresent`）、開発 `develop-<commit-sha>`（`imagePullPolicy: Always`） |
| DB | アプリごとに専用の MariaDB を [mariadb-operator](https://github.com/mariadb-operator/mariadb-operator)（`k8s.mariadb.com/v1alpha1`）で構築。共有DBインスタンスは使わない |
| ストレージクラス | `rook-ceph-rbd`（DB等のブロックデバイス）、`rook-cephfs`（共有ファイルシステム） |
| Secret管理 | 平文はgitに置かない。`k8s.bitwarden.com/v1 BitwardenSecret` CRD で Bitwarden Secrets Manager から同期する。新規namespaceは `kigawa-system/secret-provider/bitwarden-sync-crn.yaml` の `TARGET_NAMESPACES` に追記が必要（`bitwarden-sec` 認証トークンの同期用） |
| 認証 | Keycloak（`user.kigawa.net`）。アプリによって専用realmと共有realm（`kigawa-net`）が混在。requirements.md の通りLiplは専用realm（`lipl`）を新設する方針で問題ない |

**cert-manager は `platform` 内に存在しない。** 既存アプリはすべて `kigawa.net` サブドメインのみを使い、独自ドメイン機能を持たないため、cert-manager を使う必要がなかったと考えられる。**Lipl の独自ドメイン機能（Pro）はこのクラスタにとって新規の技術要素であり、cert-manager の導入が前提になる。**

## 要件定義への訂正（このドキュメント作成中に発見）

`docs/requirements.md` の「ネットワーク」節に技術的な矛盾がある。

- 店舗ドメインの形式は `<slug>-lipl.kigawa.net`。これは `kigawa.net` ゾーンにおける**1ラベルのサブドメイン**であり、ラベル内に `-lipl` という文字列を含むだけである
- 一方で「ワイルドカード DNS `*.lipl.kigawa.net` で一括解決」と書かれているが、これは `lipl.kigawa.net` を親ゾーンとした**2階層目のサブドメイン**を指すワイルドカードであり、`<slug>-lipl.kigawa.net`（1階層目）にはマッチしない
- DNSワイルドカードは1ラベル位置全体にのみマッチし、ラベル内の部分文字列（サフィックス）に対しては機能しない。したがって `*-lipl.kigawa.net` のような書き方も同様に成立しない
- 実際に必要なのは `*.kigawa.net`（`kigawa.net` 直下の全サブドメインをカバーするワイルドカード）であり、これは既存アプリ（`ktse.kigawa.net` 等）が個別のDNSレコードなしに解決されているように見えることから、**既にクラスタ側で運用中の可能性が高い**（未確認）

`docs/requirements.md` の該当行を「DNS解決は `*.kigawa.net` ワイルドカード（既存想定）に依存し、Lipl側で新たなワイルドカードDNSは不要」という記述に修正する必要がある（別PRで対応）。

## コンポーネント構成

`platform` リポジトリの `lipl/main/` に以下を配置する。

```
lipl/main/
├── ns.yaml                  # Namespace: kigawa-net-lipl-main
├── lipl-frontend.yaml        # Deployment + Service + BitwardenSecret
├── lipl-api.yaml              # Deployment + Service + BitwardenSecret
├── mariadb-lipl.yml          # MariaDB + Database + User + Grant (CRD)
├── ingress.yaml              # Ingress（lipl.kigawa.net、<slug>-lipl.kigawa.net 用ワイルドカード）
├── custom-domain-rbac.yaml   # lipl-api が動的にIngress/Certificateを作成するためのRBAC
└── cert-manager-issuer.yaml  # ClusterIssuer（クラスタ未導入の場合のみ、新設が必要）
```

既存の `keruta/main/`（`ktse.yaml` + `ktcl-front.yaml` + `mariadb-ktse.yml` + `ingress.yaml`）と同型の構成。

### lipl-frontend

- React Router v8（SSR）。ポート `3000`
- 既存の `lp`/`ktcl-front` と同様、`imagePullSecrets: harbor-registry`、`harbor.kigawa.net/private/lipl-frontend:main-<sha>`
- Keycloak（`user.kigawa.net`、realm `lipl`）と連携する OIDC クライアント設定を環境変数で注入

### lipl-api

- Ktor。ポート `8080`
- `DB_JDBC_URL=jdbc:mysql://mariadb-lipl:3306/lipl`（`mariadb-ktse` と同じパターン）
- Keycloak JWT検証用の issuer/JWKS URL
- Stripe / Claude API / オブジェクトストレージの認証情報は BitwardenSecret 経由で注入
- カスタムドメイン機能のため、`kigawa-net-lipl-main` namespace内で `Ingress` と `Certificate`（cert-manager）を作成・削除できる RBAC を持つ ServiceAccount を使用する（下記「独自ドメイン」参照）

### mariadb-lipl

`keruta/main/mariadb-ktse.yml` と同型：

```yaml
apiVersion: k8s.mariadb.com/v1alpha1
kind: MariaDB
metadata:
  name: mariadb-lipl
spec:
  rootPasswordSecretKeyRef:
    name: mariadb-lipl
    key: root-pass
  image: mariadb:10.11
  storage:
    size: 5Gi          # 写真本体はオブジェクトストレージ側のため、DBはメタデータ主体で小さめでよい
    storageClassName: rook-ceph-rbd
  resources:
    requests: { memory: "256Mi", cpu: "100m" }
    limits: { memory: "1Gi", cpu: "1000m" }
---
apiVersion: k8s.mariadb.com/v1alpha1
kind: Database
metadata: { name: lipl }
spec:
  mariaDbRef: { name: mariadb-lipl }
  name: lipl
  characterSet: utf8mb4
  collate: utf8mb4_unicode_ci
---
# User / Grant は ktse と同型
```

## Ingress・ドメイン・TLS

### デフォルトドメイン（`<slug>-lipl.kigawa.net`、全プラン）

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: lipl
spec:
  ingressClassName: haproxy
  rules:
    - host: lipl.kigawa.net          # ダッシュボード・LP生成API
      http: { ... service: lipl-frontend ... }
    - host: "*.lipl.kigawa.net"      # 要修正（上記「要件定義への訂正」参照）
      http: { ... service: lipl-frontend ... }
```

- `<slug>-lipl.kigawa.net` 形式では、HAProxy Ingress の host マッチングにワイルドカードではなく `*.kigawa.net`（クラスタ側で既に解決されている前提）+ アプリ内での Host ヘッダ判定 で対応する。具体的には、Ingress に `lipl.kigawa.net` の1ホストのみを明示的に登録し、**それ以外のすべての未知ホストを lipl-frontend にフォールバックさせる**（HAProxy Ingress の default-backend 設定、または `path-type: ImplementationSpecific` + ワイルドカードでない任意ホスト受け）方式が必要になる。この挙動は他アプリにない特殊要件なので、実装時に HAProxy Ingress Controller の挙動を検証すること
- リクエストを受けた `lipl-frontend`（および `lipl-api`）は `Host` ヘッダから `<slug>` 部分を抜き出し、DBで店舗を解決する（アプリケーションレベルのマルチテナントルーティング。requirements.md の「Ingress は slug をホスト名によりルーティング」はこの意味で読み替える）

### 独自ドメイン（Pro）

`requirements.md` で規定した「CNAME到達 + cert-manager HTTP-01チャレンジ成功で検証済みとみなす」を実現するため:

1. cert-manager をクラスタに導入する（未導入の場合。`kinfra`/`infra` リポジトリ側の管理範囲になる可能性があるため要確認）
2. `ClusterIssuer`（Let's Encrypt、HTTP-01、`ingressClass: haproxy` で解決）を1つ用意する
3. ユーザーが独自ドメインを登録すると、`lipl-api` が Kubernetes API 経由で以下を動的に作成する:
   - `Ingress`（該当ドメインをhostに持ち、`lipl-frontend` Serviceへ）
   - `Certificate`（cert-manager、上記Issuerを参照）
4. `lipl-api` の ServiceAccount に `kigawa-net-lipl-main` namespace内での `ingresses`・`certificates.cert-manager.io` の作成/更新/削除権限を持つ `Role`/`RoleBinding` を付与する
5. 証明書発行成功（`Certificate` の `Ready` 条件）を `lipl-api` がポーリングまたは Watch し、DBの「検証済み」フラグを更新する

## Secrets（Bitwarden）

必要なシークレットと、対応する `BitwardenSecret` の想定キー:

| Secret名 | キー | 用途 |
|----------|------|------|
| `mariadb-lipl` | `root-pass`, `pass` | MariaDB root/appユーザーパスワード |
| `lipl-api` | `stripe-secret-key`, `stripe-webhook-secret`, `claude-api-key`, `keycloak-client-secret`, `s3-access-key`, `s3-secret-key` | 外部サービス連携 |
| `lipl-frontend` | `keycloak-client-secret`（OIDC confidential clientの場合） | フロントエンドのKeycloak連携 |

実際の `bwSecretId`（Bitwarden側のUUID）は、Bitwarden Secrets Manager 上で事前に値を作成した上で取得する。ここでは値そのものは扱わない。

新規namespace `kigawa-net-lipl-main` を `kigawa-system/secret-provider/bitwarden-sync-crn.yaml` の `TARGET_NAMESPACES` に追加すること（`bitwarden-sec` 認証トークンが同期されないと `BitwardenSecret` オペレータが機能しない）。

## オブジェクトストレージ（未確定・要検討）

`platform` 内に既存の S3互換オブジェクトストレージは見つからなかった。選択肢:

1. **クラスタ内 MinIO**（`rook-cephfs` 上に StatefulSet + PVC）— 自前で完全に管理できるが運用負荷が増える
2. **Cloudflare R2**（外部・S3互換）— 運用負荷なし、エグレス無料。kigawa-net系プロジェクトでCloudflareを多用している実績があり親和性が高い
3. **Rook-Cephのオブジェクトゲートウェイ（RGW）** — クラスタのCephクラスタが既にRGWを提供していれば追加インフラ不要だが、有効化状況は未確認

**推奨: 2（Cloudflare R2）**。運用負荷が最小で、写真アップロードのエグレス（LP閲覧時の画像配信）コストもかからない。要ユーザー確認。

## CI/CD

1. GitHub Actions（`lipl` リポジトリ）: push to `main` → Docker イメージビルド（frontend/backend）→ `harbor.kigawa.net/private/lipl-frontend:main-<sha>` / `lipl-api:main-<sha>` へ push
2. 同ワークフロー、または後続ジョブが `platform` リポジトリの `lipl/main/lipl-frontend.yaml` / `lipl-api.yaml` の image タグを更新してコミット（他アプリのCIパターンを参考に実装。具体的な既存ワークフローは各アプリのソースリポジトリ側にあり、今回は未調査）
3. ArgoCD が `platform` の変更を検知し自動sync

## リソース見積もり（初期・個人開発規模）

| コンポーネント | replicas | CPU request/limit | Memory request/limit |
|--------------|----------|-------------------|----------------------|
| lipl-frontend | 1 | 100m / 500m | 128Mi / 256Mi |
| lipl-api | 1 | 100m / 500m | 256Mi / 512Mi |
| mariadb-lipl | 1 | 100m / 500m | 256Mi / 512Mi |

MVP規模ではHPA（自動スケール）は不要。将来的な需要増に応じて `replicas` を手動で増やす運用で十分。

## 未確定・要確認事項（実装着手前に解決すべき）

1. **DNS**: `*.kigawa.net` ワイルドカードが実際に既存かどうかの確認（kinfra/infra リポジトリまたはDNSプロバイダ側の設定を確認）
2. **HAProxy Ingressのフォールバックルーティング**: `<slug>-lipl.kigawa.net` の動的な多数ホストを1つのIngressでどう受けるか（ワイルドカードhost指定が可能か、あるいはdefault-backend方式にするか）の技術検証
3. **cert-manager導入**: このクラスタに未導入。独自ドメイン機能の実装前に導入が必要（管理範囲がkinfra/infra側かplatform側か要確認）
4. **オブジェクトストレージ**: MinIO自前ホスト vs Cloudflare R2 vs Ceph RGW のいずれにするか要決定
5. **harbor-registry pull secret**: 新規namespaceでの同期方法（既存の仕組みを流用できるか、手動作成が必要か）を確認
