# Lipl Infrastructure Design

> 関連ドキュメント: [requirements.md](requirements.md) | [../README.md](../README.md) | [../CLAUDE.md](../CLAUDE.md)

このドキュメントは `docs/requirements.md` の要件を、[kigawa-net/platform](https://github.com/kigawa-net/platform) で ArgoCD GitOps 管理されているクラスタの上にどう実装するかを定義する。Lipl はこのリポジトリに新規アプリとしてデプロイする最初のアプリケーションになる。

> **注記**: `platform` は既存の [kigawa-net-k8s](https://github.com/kigawa-net/kigawa-net-k8s) とは独立した新規リポジトリで、同一クラスタを対象とする。既存アプリ（keruta, lp 等）は `kigawa-net-k8s` 側に残り、移行は行っていない。そのため `platform` は AppProject（`platform`）・Namespaceプレフィックス（`platform-*`）を `kigawa-net-k8s` 側（AppProject `kigawa-net`、Namespaceプレフィックス `kigawa-net-*`）とは独立させ、リソース競合を避けている。Ingress Class・レジストリ・Secret管理・DB構成・ストレージクラス等の**クラスタ共通設定**は同一クラスタ上のため両リポジトリで共有する。

## 前提（既存インフラの調査結果）

`kigawa-net-k8s` リポジトリの既存アプリ（keruta, lp 等）を調査し、クラスタ共通の規約を確認した。`platform` リポジトリはこれらの共通設定を引き継ぎつつ、AppProject・Namespaceプレフィックスは独立させている。

| 項目 | 既存の規約 |
|------|-----------|
| GitOpsリポジトリ | `platform` |
| マニフェスト配置 | app本体のリポジトリ（`lipl`）には k8s マニフェストは置かず、`platform` 側に環境ごとのディレクトリ（`lipl/dev/`, `lipl/stg/`）を配置する（`keruta/dev/`, `keruta/stg/`, `keruta/main/` と同様に環境ごとに丸ごとディレクトリを複製する方式。kustomize等のoverlayは使わない） |
| ArgoCD Application | `apps/lipl-dev-app.yml`, `apps/lipl-stg-app.yml` を `platform` リポジトリに新設し、`platform` AppProject 配下に追加 |
| Namespace命名 | `platform-lipl-dev`, `platform-lipl-stg` |
| Ingress Class | `haproxy`（nginx ではない） |
| ベースドメイン | `kigawa.net`。個々のアプリは `<app>.kigawa.net` の1ラベルサブドメインを使用し、Ingress マニフェスト自体に TLS ブロックの記載はない（クラスタ側で `kigawa.net` 系サブドメインを一括カバーするデフォルト証明書が設定されていると推測される） |
| コンテナレジストリ | `harbor.kigawa.net`（`private/<service>` パス。`imagePullSecrets: harbor-registry`） |
| イメージタグ | 本番 `main-<commit-sha>`（`imagePullPolicy: IfNotPresent`）、開発 `develop-<commit-sha>`（`imagePullPolicy: Always`） |
| DB | アプリごとに専用の MariaDB を [mariadb-operator](https://github.com/mariadb-operator/mariadb-operator)（`k8s.mariadb.com/v1alpha1`）で構築。共有DBインスタンスは使わない |
| ストレージクラス | `rook-ceph-rbd`（DB等のブロックデバイス）、`rook-cephfs`（共有ファイルシステム） |
| Secret管理 | 平文はgitに置かない。`k8s.bitwarden.com/v1 BitwardenSecret` CRD で Bitwarden Secrets Manager から同期する。新規namespaceは `kigawa-system/secret-provider/bitwarden-sync-crn.yaml` の `TARGET_NAMESPACES` に追記が必要（`bitwarden-sec` 認証トークンの同期用） |
| 認証 | Keycloak（`user.kigawa.net`）。アプリによって専用realmと共有realm（`kigawa-net`）が混在。requirements.md の通りLiplは専用realm（`lipl`）を新設する方針で問題ない |

## デプロイフロー（dev / stg）

Lipl は2環境構成で運用する（現時点で本番専用の第3環境「main」は用意しない。将来的に必要になれば `keruta` と同様の3環境構成に拡張する）。

| トリガー | 環境 | Namespace | ブランチ/イメージタグ |
|---------|------|-----------|----------------------|
| `lipl` リポジトリで PR に `deploy-preview` ラベルを付与 | **dev**（PRごとに独立） | `platform-lipl-dev-pr-<PR番号>` | `develop-<commit-sha>` |
| `lipl` リポジトリの `main` へマージ | **stg** | `platform-lipl-stg` | `main-<commit-sha>`、`imagePullPolicy: IfNotPresent` |

- **dev環境はPRごとに独立させる**。ArgoCD ApplicationSet（`platform` リポジトリの `apps/lipl-dev-appset.yml`）のPull Request Generatorが、`lipl` リポジトリで開いているPRを検出して自動的にApplication・namespace（`platform-lipl-dev-pr-<PR番号>`）を生成する。イメージタグ（`develop-<head_sha>`）もPRの最新コミットに追従してKustomize経由で動的に上書きされる
  - `lipl` はpublicリポジトリのため誰でもフォークからPRを作成できる。ラベルフィルタなしでは外部の任意のPRごとにnamespace/Deploymentが自動生成されてしまう（クラスタリソース濫用のリスク）。これを防ぐため、ApplicationSet側で `github.labels: [deploy-preview]` フィルタを設定し、メンテナが明示的に `deploy-preview` ラベルを付与したPRのみdev環境を生成する運用にする。CI（`.github/workflows/deploy-dev.yml`）自体はラベルに関わらず全PRでテスト・イメージビルドを実行する（テストの早期フィードバックのため）
  - `lipl` 側CI（`.github/workflows/deploy-dev.yml`）はDockerイメージのビルド・pushのみを行う。`platform` リポジトリへのマニフェスト更新コミットは不要（ApplicationSetが直接制御するため）
  - PRがクローズ/マージされると対応するApplicationは自動的に削除される（`resources-finalizer.argocd.argoproj.io` によりDeployment/Serviceはカスケード削除。namespace自体の削除挙動はArgoCDバージョン依存のため要検証。残存する場合は定期的な確認・削除運用を検討）
  - dev環境ではIngressを持たない（PR個別のドメイン割り当てはHAProxy Ingressの動的ホスト対応が未検証のため見送り。動作確認は `kubectl port-forward` 等で行う）
- **stg環境が実質的にLiplの唯一の稼働環境**（現時点で別途の本番環境はない）。`docs/requirements.md` に記載の実ドメイン（`lipl.kigawa.net`、`<slug>-lipl.kigawa.net`）はstg環境に割り当てる
- **外部サービスの認証情報はdev/stgで分離する**: Stripeはdev環境でテストモードのAPIキーを使う（本番決済情報を扱わない）。Keycloakのrealm/clientもdev/stgで分離する（`lipl-dev` client等）。Claude APIキーはコスト管理のため分離を推奨
- dev/stg それぞれに専用の MariaDB（`mariadb-lipl-dev` / `mariadb-lipl-stg`）を用意する（データを共有しない）
- オブジェクトストレージ（Cloudflare R2）はdev/stgでバケットまたはキープレフィックスを分離する（例: `lipl-photos-dev` / `lipl-photos`）

**cert-manager は `platform` 内に存在しない。** 既存アプリはすべて `kigawa.net` サブドメインのみを使い、独自ドメイン機能を持たないため、cert-manager を使う必要がなかったと考えられる。**Lipl の独自ドメイン機能（Pro）はこのクラスタにとって新規の技術要素であり、cert-manager の導入が前提になる。**

## 要件定義への訂正（このドキュメント作成中に発見）

`docs/requirements.md` の「ネットワーク」節に技術的な矛盾がある。

- 店舗ドメインの形式は `<slug>-lipl.kigawa.net`。これは `kigawa.net` ゾーンにおける**1ラベルのサブドメイン**であり、ラベル内に `-lipl` という文字列を含むだけである
- 一方で「ワイルドカード DNS `*.lipl.kigawa.net` で一括解決」と書かれているが、これは `lipl.kigawa.net` を親ゾーンとした**2階層目のサブドメイン**を指すワイルドカードであり、`<slug>-lipl.kigawa.net`（1階層目）にはマッチしない
- DNSワイルドカードは1ラベル位置全体にのみマッチし、ラベル内の部分文字列（サフィックス）に対しては機能しない。したがって `*-lipl.kigawa.net` のような書き方も同様に成立しない
- 実際に必要なのは `*.kigawa.net`（`kigawa.net` 直下の全サブドメインをカバーするワイルドカード）であり、これは既存アプリ（`ktse.kigawa.net` 等）が個別のDNSレコードなしに解決されているように見えることから、**既にクラスタ側で運用中の可能性が高い**（未確認）

`docs/requirements.md` の該当行を「DNS解決は `*.kigawa.net` ワイルドカード（既存想定）に依存し、Lipl側で新たなワイルドカードDNSは不要」という記述に修正する必要がある（別PRで対応）。

## コンポーネント構成

`platform` リポジトリに `lipl/dev/` と `lipl/stg/` を配置する。PRごとに動的生成されるdevと、単一の静的環境であるstgとで構成が異なる。

```
lipl/stg/                     # 静的環境。既存keruta/stg/等と同様、Applicationは1つ固定
├── ns.yaml                  # Namespace: platform-lipl-stg
├── lipl-frontend.yaml        # Deployment + Service + BitwardenSecret
├── lipl-api.yaml              # Deployment + Service + BitwardenSecret
├── mariadb-lipl.yml          # MariaDB + Database + User + Grant (CRD)
├── ingress.yaml              # Ingress（lipl.kigawa.net、<slug>-lipl.kigawa.net 用）
├── custom-domain-rbac.yaml   # lipl-api が動的にIngress/Certificateを作成するためのRBAC（独自ドメインはPro機能）
└── cert-manager-issuer.yaml  # ClusterIssuer（クラスタ未導入の場合のみ、新設が必要）

lipl/dev/                     # 動的環境。ApplicationSet（apps/lipl-dev-appset.yml）がPRごとに
│                              # namespace・imageタグを上書きしてApplicationを生成するKustomizeベース
├── kustomization.yaml        # namespace/images はApplicationSetのテンプレートから注入（ベースには持たせない）
├── lipl-frontend.yaml        # Deployment + Service
└── lipl-api.yaml              # Deployment + Service
                               # ns.yaml・ingress.yaml・mariadb・BitwardenSecretはdevには置かない
                               # （namespaceはCreateNamespace=true任せ、Ingressは動的ホスト対応が未検証のため見送り、
                               #   DB/Secretは対応機能がLipl側に未実装のため後日追加）
```

stgは既存の `keruta/main/`（`ktse.yaml` + `ktcl-front.yaml` + `mariadb-ktse.yml` + `ingress.yaml`）と同型の静的構成。devはArgoCD ApplicationSetのPull Request Generatorパターンを採用しており、既存アプリには無い新しい構成。

### lipl-frontend

- React Router v8（SSR）。ポート `3000`
- 既存の `lp`/`ktcl-front` と同様、`imagePullSecrets: harbor-registry`、`harbor.kigawa.net/private/lipl-frontend:main-<sha>`
- Keycloak（`user.kigawa.net`、realm `lipl`）と連携する OIDC クライアント設定を環境変数で注入

### lipl-api

- Ktor。ポート `8080`
- `DB_JDBC_URL=jdbc:mysql://mariadb-lipl:3306/lipl`（`mariadb-ktse` と同じパターン）
- Keycloak JWT検証用の issuer/JWKS URL
- Stripe / Claude API / オブジェクトストレージの認証情報は BitwardenSecret 経由で注入
- カスタムドメイン機能のため、`platform-lipl-stg` namespace内で `Ingress` と `Certificate`（cert-manager）を作成・削除できる RBAC を持つ ServiceAccount を使用する（下記「独自ドメイン」参照）

### mariadb-lipl

`keruta/stg/mariadb-ktse.yml` と同型（`name`, `size` 等はdev/stgで共通のテンプレートを想定。`mariadb-lipl` という名前自体はnamespaceで分離されるためdev/stgで同名でよい）：

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

以下は stg環境の例（dev環境は `lipl.kigawa.net` → `lipl-dev.kigawa.net`、`*.lipl.kigawa.net` → `*.lipl-dev.kigawa.net` に置き換える）:

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
4. `lipl-api` の ServiceAccount に `platform-lipl-stg` namespace内での `ingresses`・`certificates.cert-manager.io` の作成/更新/削除権限を持つ `Role`/`RoleBinding` を付与する
5. 証明書発行成功（`Certificate` の `Ready` 条件）を `lipl-api` がポーリングまたは Watch し、DBの「検証済み」フラグを更新する

## Secrets（Bitwarden）

必要なシークレットと、対応する `BitwardenSecret` の想定キー:

| Secret名 | キー | 用途 |
|----------|------|------|
| `mariadb-lipl` | `root-pass`, `pass` | MariaDB root/appユーザーパスワード |
| `lipl-api` | `stripe-secret-key`, `stripe-webhook-secret`, `claude-api-key`, `keycloak-client-secret`, `r2-access-key-id`, `r2-secret-access-key` | 外部サービス連携（`r2-*` はCloudflare R2のS3互換APIトークン） |
| `lipl-frontend` | `keycloak-client-secret`（OIDC confidential clientの場合） | フロントエンドのKeycloak連携 |

上記はdev/stg各namespaceに同名で配置する（`platform-lipl-dev` と `platform-lipl-stg` それぞれに `mariadb-lipl`, `lipl-api`, `lipl-frontend` という名前のBitwardenSecretを作る。namespace分離により名前は衝突しない）。**`bwSecretId`（値）はdev/stgで別々のものを使う**（特にStripeはdevでテストモードキー、stgで本番キーを参照するように、Bitwarden Secrets Manager側で別々のシークレットとして管理する）。

新規namespace `platform-lipl-dev`, `platform-lipl-stg` を `kigawa-system/secret-provider/bitwarden-sync-crn.yaml` の `TARGET_NAMESPACES` に追加すること（`bitwarden-sec` 認証トークンが同期されないと `BitwardenSecret` オペレータが機能しない）。

## オブジェクトストレージ

**Cloudflare R2 を採用する（確定）**。`platform` 内に既存のS3互換オブジェクトストレージは見つからず、クラスタ内MinIOやRook-Ceph RGWのような自前ホスト型ではなく、運用負荷ゼロ・エグレス無料の外部S3互換サービスであるR2を選ぶ。

- 写真アップロード（`requirements.md` の「写真アップロード制約」参照）は R2 バケットに保存する
- バケット構成: 用途ごとに1バケット（例: `lipl-photos`）。店舗ID・写真IDでキー空間を分ける（例: `stores/<storeId>/photos/<photoId>.jpg`）
- `lipl-api` から R2 の S3互換APIエンドポイントへアクセスする（アクセスキー/シークレットキーは Secrets 節の `lipl-api` BitwardenSecret に含める）
- QRコード仕様・写真アップロード制約で規定された解像度リサイズ（フルHD/4K上限）はアップロード時に `lipl-api` 側で行い、リサイズ後の画像をR2に保存する（R2側の変換機能には依存しない）
- LP公開時の画像配信は R2 のパブリックバケット機能、またはCloudflare経由のカスタムドメイン配信を利用し、エグレスコストを回避する

## CI/CD

GitHub Actions（`lipl` リポジトリ）で2系統のワークフローを持つ。

### dev（PR作成・更新時）

1. PRイベント（`opened`, `synchronize`）をトリガーに Docker イメージビルド（frontend/backend）→ `harbor.kigawa.net/private/lipl-frontend:develop-<sha>` / `lipl-api:develop-<sha>` へ push
2. 後続ジョブが `platform` リポジトリの `lipl/dev/lipl-frontend.yaml` / `lipl-api.yaml` の image タグを更新してコミット
3. ArgoCD が `platform` の変更を検知し `platform-lipl-dev` へ自動sync

### stg（`main` へのマージ時）

1. push to `main` をトリガーに Docker イメージビルド → `harbor.kigawa.net/private/lipl-frontend:main-<sha>` / `lipl-api:main-<sha>` へ push
2. 後続ジョブが `platform` リポジトリの `lipl/stg/lipl-frontend.yaml` / `lipl-api.yaml` の image タグを更新してコミット
3. ArgoCD が `platform` の変更を検知し `platform-lipl-stg` へ自動sync

実装は `.github/workflows/deploy-dev.yml`（PR時）/ `deploy-stg.yml`（`main`マージ時）。各ワークフローは以下の順で実行する: バックエンド（`./gradlew build`）・フロントエンド（`npm run lint && typecheck && build`）のテスト → 両方成功した場合のみ Docker イメージビルド・push → `platform` リポジトリのマニフェスト更新・コミット・push。

### Secret管理（Bitwarden Secrets Manager）

クラスタ内のSecret管理と同様、CI（GitHub Actions）側のSecretも生の値をGitHubに直接登録せず、Bitwarden Secrets Manager から [`bitwarden/sm-action`](https://github.com/bitwarden/sm-action) 経由で取得する。

- `lipl` リポジトリに設定するGitHub Secretは **`BWS_ACCESS_TOKEN`（Bitwarden Machine Account のアクセストークン）1つのみ**（未設定。要対応）
- ワークフロー内で以下をBitwarden Secrets Managerから取得する:

| 環境変数名 | 用途 | Bitwarden Secret | 状態 |
|-----------|------|-------------------|------|
| `HARBOR_USERNAME` / `HARBOR_PASSWORD` | `harbor.kigawa.net` への `docker login` | 既存の `harbor-user` / `harbor-pass`（`keruta` 等と共通） | 設定済み |
| `GH_APP_ID` / `GH_APP_PRIVATE_KEY` | `kigawa-net/platform` へのpush用トークンを [`actions/create-github-app-token`](https://github.com/actions/create-github-app-token) で発行するための GitHub App 認証情報 | 既存の GitHub App **`kigawa-net`**（app_id `4316503`、`contents: write` 権限、org全体にインストール済み）。app_idを新規Secret `github-app-kigawa-net-appid` として作成し、既存の private key Secret `github-app-kigawa-net` と組み合わせて使用 | 設定済み |

**PATではなくGitHub Appを採用した理由**: 長期間有効な生のPATをBitwardenに保管する代わりに、ワークフロー実行時に短命なインストールトークンを都度発行する方式にすることで、トークン漏洩時の影響範囲と有効期間を抑える。既存インフラに `contents: write` 権限を持つ組織共通のGitHub App（`kigawa-net`）が既にorg全体へインストール済みだったため、新規App作成は不要だった。

Bitwarden Secrets Manager側の Organization ID は既存インフラと共通（`a2b57f3d-6e2b-4467-b499-b31e00bfd804`、`kigawa-net-k8s` のCLAUDE.md参照）。Secret IDは各ワークフローファイル（`.github/workflows/deploy-dev.yml` / `deploy-stg.yml`）に既に反映済み。

## リソース見積もり（初期・個人開発規模）

dev/stgそれぞれに以下を配置する（合計は2倍）。

| コンポーネント | replicas | CPU request/limit | Memory request/limit |
|--------------|----------|-------------------|----------------------|
| lipl-frontend | 1 | 100m / 500m | 128Mi / 256Mi |
| lipl-api | 1 | 100m / 500m | 256Mi / 512Mi |
| mariadb-lipl | 1 | 100m / 500m | 256Mi / 512Mi |

MVP規模ではHPA（自動スケール）は不要。dev環境は検証専用のため、replicasを1未満（スケールダウン）にして常時稼働させない運用も検討可（keruta同様、使用状況次第でreplicas: 0にする選択肢もある）。将来的な需要増に応じて `replicas` を手動で増やす運用で十分。

## 未確定・要確認事項（実装着手前に解決すべき）

1. ~~**DNS**: `*.kigawa.net` ワイルドカードが実際に既存かどうかの確認~~ → 解決済み。実クラスタで確認したところ `lipl` Ingress（`lipl.kigawa.net`）に実際にLoadBalancer IPが割り当たっており、名前解決も機能している
2. ~~**HAProxy Ingressのフォールバックルーティング**~~ → stg環境の単一ホスト（`lipl.kigawa.net`）については実クラスタで動作確認済み（Ingressにアドレス割当・Pod到達可能）。`<slug>-lipl.kigawa.net` 形式の動的な多数ホストのルーティング方式自体は、該当機能（店舗管理・公開LP）の実装時にあらためて検証が必要
3. ~~**cert-manager導入**~~ → 解決済み。実クラスタで `cert-manager` ArgoCD Applicationが `Synced`/`Healthy` で稼働していることを確認した（導入済み）
4. ~~**harbor-registry pull secret（PRごとに動的生成されるdev namespace向け）**~~ → 解決済み。`kigawa-system/secret-provider` のCronJobを、静的 `TARGET_NAMESPACES` リストに加えて `platform-lipl-dev-pr-*` にマッチする実在namespaceを正規表現で動的検出するロジックに拡張した（[kigawa-net-k8s#195](https://github.com/kigawa-net/kigawa-net-k8s/pull/195)）。実クラスタで `platform-lipl-stg` への `harbor-registry` シークレット同期・Podの正常pullを確認済み
5. ~~**PR単位のdev環境上書きによる競合**~~ → 解決済み。ArgoCD ApplicationSet（Pull Request Generator）でPRごとに独立したnamespaceを動的生成する方式に変更した（[kigawa-net/platform#1](https://github.com/kigawa-net/platform/pull/1)）。lipl はpublicリポジトリのため、`deploy-preview` ラベルが付与されたPRのみを対象にするフィルタも追加済み
6. ~~**`platform` リポジトリのArgoCD登録**: 手動 `kubectl apply` が必要~~ → 解決済み。既存の `kigawa-net-k8s` のルートApp（`apps/apps-app.yml`、稼働中）の再帰同期を利用し、`platform-app`（`platform` リポジトリをブートストラップするApplication）を `kigawa-net-k8s` の `apps/` 配下に追加した（[kigawa-net-k8s#195](https://github.com/kigawa-net/kigawa-net-k8s/pull/195)）。実クラスタで `platform-app` / `platform-lipl-stg-app` が `Synced`/`Healthy` であることを確認済み
7. ~~**ApplicationSetコントローラーの有効化確認**~~ → 解決済み。実クラスタで `argocd-applicationset-controller` Podが稼働中、`platform-lipl-dev-appset` ApplicationSetも登録済みであることを確認した
8. **secret-provider CronJob（`bitwarden-sync-crn`）のPodがノードで長時間 `ContainerCreating` のままスタックする問題**: 実クラスタ確認で、`bitwarden-sync-crn` のJobが直近4時間で27個 `active` のまま蓄積し、最終成功時刻が約1ヶ月前（`2026-07-27`）だったことが判明した。手動実行したテストPodも `k8s-worker5` で `FailedCreatePodSandBox: context deadline exceeded` エラーで同様にスタックした。これは今回のYAML修正（[kigawa-net-k8s#196](https://github.com/kigawa-net/kigawa-net-k8s/pull/196)、TARGET_NAMESPACES内の存在しない `github-runner` エントリと `set -e` による早期中断の修正）とは別の、ノード/コンテナランタイムレベルの問題と見られる。`harbor-sync-crn` は同時期に直近で成功しており（`platform-lipl-stg` に `harbor-registry` シークレット同期済み）、影響は `bitwarden-sync-crn` 側のみか一部ノードに限定される可能性がある。`platform-lipl-stg` に `bitwarden-sec` が未同期の状態（現時点でlipl本体はBitwarden secretを実行時に使用していないため実害はないが、要調査・要対応）
