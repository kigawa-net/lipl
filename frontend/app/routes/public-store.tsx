import { useEffect, useState } from "react";
import { useParams } from "react-router";
import { getPublicStore, photoUrl, type PublicStoreResponse } from "~/lib/api";
import { BUSINESS_CATEGORY_LABELS, SNS_PLATFORM_LABELS } from "~/lib/labels";

export default function PublicStore() {
  const { slug } = useParams();
  const [store, setStore] = useState<PublicStoreResponse | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!slug) return;
    getPublicStore(slug)
      .then(setStore)
      .catch((e: Error) => setError(e.message));
  }, [slug]);

  if (error) {
    return (
      <main className="flex min-h-screen items-center justify-center p-8 text-center dark:bg-stone-950">
        <p className="text-red-600 dark:text-red-400">{error}</p>
      </main>
    );
  }

  if (store === undefined) {
    return (
      <main className="flex min-h-screen items-center justify-center dark:bg-stone-950">
        <p className="text-gray-400 dark:text-stone-500">読み込み中...</p>
      </main>
    );
  }

  if (store === null) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-3 p-8 text-center dark:bg-stone-950">
        <h1 className="text-xl font-bold text-gray-700 dark:text-stone-200">ページが見つかりません</h1>
        <p className="text-sm text-gray-500 dark:text-stone-500">
          このページは非公開になっているか、存在しません。
        </p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-amber-50/40 dark:bg-stone-950">
      {store.photos.length > 0 && (
        <div className="grid grid-cols-2 gap-1 sm:grid-cols-3">
          {store.photos.map((photo) => (
            <img
              key={photo.id}
              src={photoUrl(store.kaftBaseUrl, photo)}
              alt={photo.filename}
              className="aspect-square w-full object-cover"
            />
          ))}
        </div>
      )}

      <div className="mx-auto max-w-2xl px-6 py-10">
        <span className="inline-block rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-800 dark:bg-amber-900/40 dark:text-amber-400">
          {BUSINESS_CATEGORY_LABELS[store.businessCategory]}
        </span>
        <h1 className="mt-3 text-3xl font-bold text-gray-900 dark:text-stone-100">{store.name}</h1>

        <dl className="mt-6 space-y-2 text-sm text-gray-700 dark:text-stone-300">
          {(store.address || store.businessArea) && (
            <div className="flex gap-2">
              <dt className="w-20 shrink-0 text-gray-400 dark:text-stone-500">
                {store.operationType === "FIXED" ? "所在地" : "出店エリア"}
              </dt>
              <dd>{store.address ?? store.businessArea}</dd>
            </div>
          )}
          {store.businessHours && (
            <div className="flex gap-2">
              <dt className="w-20 shrink-0 text-gray-400 dark:text-stone-500">営業時間</dt>
              <dd>{store.businessHours}</dd>
            </div>
          )}
          {store.phone && (
            <div className="flex gap-2">
              <dt className="w-20 shrink-0 text-gray-400 dark:text-stone-500">電話番号</dt>
              <dd>{store.phone}</dd>
            </div>
          )}
        </dl>

        {store.snsLinks.length > 0 && (
          <div className="mt-5 flex flex-wrap gap-2">
            {store.snsLinks.map((link) => (
              <a
                key={link.platform}
                href={link.url}
                target="_blank"
                rel="noreferrer"
                className="rounded-full border border-amber-200 bg-white px-3 py-1 text-xs font-semibold text-amber-800 transition-colors hover:bg-amber-50 dark:border-amber-800 dark:bg-stone-900 dark:text-amber-400 dark:hover:bg-stone-800"
              >
                {SNS_PLATFORM_LABELS[link.platform]}
              </a>
            ))}
          </div>
        )}

        {store.menuItems.length > 0 && (
          <div className="mt-10">
            <h2 className="mb-4 text-lg font-bold text-gray-900 dark:text-stone-100">メニュー</h2>
            <ul className="space-y-3">
              {store.menuItems.map((item) => (
                <li
                  key={item.id}
                  className="rounded-lg border border-amber-100 bg-white p-4 dark:border-stone-800 dark:bg-stone-900"
                >
                  <div className="flex items-baseline justify-between gap-4">
                    <span className="font-semibold text-gray-900 dark:text-stone-100">{item.name}</span>
                    {item.price != null && (
                      <span className="shrink-0 text-sm text-gray-500 dark:text-stone-400">
                        ¥{item.price.toLocaleString()}
                      </span>
                    )}
                  </div>
                  {item.description && (
                    <p className="mt-1 text-sm text-gray-500 dark:text-stone-400">{item.description}</p>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </main>
  );
}
