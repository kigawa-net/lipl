import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import {
  deleteStore,
  getAiUsage,
  getDebugConfig,
  listStores,
  setAiUsage,
  type AiUsageResponse,
  type StoreResponse,
} from "~/lib/api";
import { BUSINESS_CATEGORY_LABELS, OPERATION_TYPE_LABELS } from "~/lib/labels";
import { isAuthenticated } from "~/lib/oidc";

export default function Dashboard() {
  const navigate = useNavigate();
  const [stores, setStores] = useState<StoreResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const [debugMenuEnabled, setDebugMenuEnabled] = useState(false);
  const [aiUsage, setAiUsageState] = useState<AiUsageResponse | null>(null);
  const [aiUsageInput, setAiUsageInput] = useState("");
  const [aiUsageSaving, setAiUsageSaving] = useState(false);
  const [aiUsageError, setAiUsageError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated()) {
      navigate("/login", { replace: true });
      return;
    }
    listStores()
      .then(setStores)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));

    getDebugConfig()
      .then((config) => {
        setDebugMenuEnabled(config.debugMenuEnabled);
        if (config.debugMenuEnabled) {
          getAiUsage()
            .then((usage) => {
              setAiUsageState(usage);
              setAiUsageInput(String(usage.generationCount));
            })
            .catch((e: Error) => setAiUsageError(e.message));
        }
      })
      .catch(() => {
        // デバッグ設定の取得失敗は無視する（本番相当の環境では未設定のため常に失敗し得る）
      });
  }, [navigate]);

  async function handleSetAiUsage(e: React.FormEvent) {
    e.preventDefault();
    setAiUsageSaving(true);
    setAiUsageError(null);
    try {
      const usage = await setAiUsage(Number(aiUsageInput));
      setAiUsageState(usage);
      setAiUsageInput(String(usage.generationCount));
    } catch (e) {
      setAiUsageError((e as Error).message);
    } finally {
      setAiUsageSaving(false);
    }
  }

  async function handleDelete(store: StoreResponse) {
    if (!window.confirm(`「${store.name}」を削除します。メニューや写真もすべて削除され、元に戻せません。よろしいですか？`)) {
      return;
    }
    setDeletingId(store.id);
    setError(null);
    try {
      await deleteStore(store.id);
      setStores((prev) => prev.filter((s) => s.id !== store.id));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setDeletingId(null);
    }
  }

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="dark:text-stone-300">読み込み中...</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold dark:text-stone-100">店舗一覧</h1>
        <a href="/logout" className="text-sm text-gray-500 underline dark:text-stone-500">
          ログアウト
        </a>
      </div>

      {error && <p className="mb-4 text-red-600 dark:text-red-400">{error}</p>}

      <ul className="mb-8 space-y-2">
        {stores.map((store) => (
          <li
            key={store.id}
            className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm dark:border-stone-800 dark:bg-stone-900 dark:shadow-none"
          >
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold dark:text-stone-100">{store.name}</span>
              <span className="text-sm text-gray-500 dark:text-stone-400">
                {BUSINESS_CATEGORY_LABELS[store.businessCategory]} /{" "}
                {OPERATION_TYPE_LABELS[store.operationType]}
              </span>
              {store.published ? (
                <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800 dark:bg-amber-900/40 dark:text-amber-400">
                  公開中
                </span>
              ) : (
                <span className="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-semibold text-gray-500 dark:bg-stone-800 dark:text-stone-400">
                  未公開
                </span>
              )}
            </div>
            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm">
              <a
                href={`/stores/${store.id}`}
                className="text-amber-800 underline hover:text-amber-900 dark:text-amber-500 dark:hover:text-amber-400"
              >
                管理する
              </a>
              {store.published && (
                <a
                  href={`/p/${store.slug}`}
                  target="_blank"
                  rel="noreferrer"
                  className="text-amber-800 underline hover:text-amber-900 dark:text-amber-500 dark:hover:text-amber-400"
                >
                  公開ページを見る
                </a>
              )}
              <button
                type="button"
                onClick={() => handleDelete(store)}
                disabled={deletingId === store.id}
                className="text-red-600 underline hover:text-red-700 disabled:opacity-50 dark:text-red-400 dark:hover:text-red-300"
              >
                {deletingId === store.id ? "削除中..." : "削除する"}
              </button>
            </div>
          </li>
        ))}
        {stores.length === 0 && <li className="text-gray-500 dark:text-stone-500">まだ店舗がありません</li>}
      </ul>

      <a
        href="/stores/new"
        className="inline-block rounded-lg bg-amber-900 px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 dark:bg-amber-700 dark:hover:bg-amber-600"
      >
        + 新しい店舗を登録する
      </a>

      {debugMenuEnabled && (
        <div className="mt-10 rounded-lg border border-dashed border-red-300 p-4 dark:border-red-800">
          <h2 className="mb-2 text-sm font-bold text-red-700 dark:text-red-400">
            デバッグメニュー（検証環境限定）
          </h2>
          {aiUsageError && <p className="mb-2 text-sm text-red-600 dark:text-red-400">{aiUsageError}</p>}
          <form onSubmit={handleSetAiUsage} className="flex items-end gap-3">
            <div className="field">
              <label className="field-label">
                <span>AI生成の実行回数</span>
                {aiUsage && <span className="field-tag">上限 {aiUsage.limit}</span>}
              </label>
              <input
                type="number"
                min={0}
                value={aiUsageInput}
                onChange={(e) => setAiUsageInput(e.target.value)}
                className="field-input"
              />
            </div>
            <button
              type="submit"
              disabled={aiUsageSaving}
              className="rounded-lg border border-red-300 px-4 py-2 text-sm font-semibold text-red-700 transition-colors hover:bg-red-50 disabled:opacity-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950"
            >
              {aiUsageSaving ? "更新中..." : "更新する"}
            </button>
          </form>
        </div>
      )}
    </main>
  );
}
