import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { listStores, type StoreResponse } from "~/lib/api";
import { BUSINESS_CATEGORY_LABELS, OPERATION_TYPE_LABELS } from "~/lib/labels";
import { isAuthenticated } from "~/lib/oidc";

export default function Dashboard() {
  const navigate = useNavigate();
  const [stores, setStores] = useState<StoreResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated()) {
      navigate("/login", { replace: true });
      return;
    }
    listStores()
      .then(setStores)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [navigate]);

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p>読み込み中...</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold">店舗一覧</h1>
        <a href="/logout" className="text-sm text-gray-500 underline">
          ログアウト
        </a>
      </div>

      {error && <p className="mb-4 text-red-600">{error}</p>}

      <ul className="mb-8 space-y-2">
        {stores.map((store) => (
          <li key={store.id} className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold">{store.name}</span>
              <span className="text-sm text-gray-500">
                {BUSINESS_CATEGORY_LABELS[store.businessCategory]} /{" "}
                {OPERATION_TYPE_LABELS[store.operationType]}
              </span>
              {store.published ? (
                <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800">
                  公開中
                </span>
              ) : (
                <span className="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-semibold text-gray-500">
                  未公開
                </span>
              )}
            </div>
            <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm">
              <a href={`/stores/${store.id}`} className="text-amber-800 underline hover:text-amber-900">
                管理する
              </a>
              {store.published && (
                <a
                  href={`/p/${store.slug}`}
                  target="_blank"
                  rel="noreferrer"
                  className="text-amber-800 underline hover:text-amber-900"
                >
                  公開ページを見る
                </a>
              )}
            </div>
          </li>
        ))}
        {stores.length === 0 && <li className="text-gray-500">まだ店舗がありません</li>}
      </ul>

      <a
        href="/stores/new"
        className="inline-block rounded-lg bg-amber-900 px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800"
      >
        + 新しい店舗を登録する
      </a>
    </main>
  );
}
