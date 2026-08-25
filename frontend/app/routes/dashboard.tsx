import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import {
  createStore,
  listStores,
  type BusinessCategory,
  type OperationType,
  type StoreResponse,
} from "~/lib/api";
import { isAuthenticated } from "~/lib/oidc";

const BUSINESS_CATEGORY_LABELS: Record<BusinessCategory, string> = {
  CAFE: "カフェ",
  IZAKAYA: "居酒屋",
  RAMEN: "ラーメン店",
  RESTAURANT: "レストラン",
  KITCHEN_CAR: "キッチンカー",
  BAR: "バー",
  TEISHOKU: "定食屋",
  OTHER: "その他",
};

const OPERATION_TYPE_LABELS: Record<OperationType, string> = {
  FIXED: "固定店舗",
  MOBILE: "移動販売",
};

function defaultOperationType(category: BusinessCategory): OperationType {
  return category === "KITCHEN_CAR" ? "MOBILE" : "FIXED";
}

export default function Dashboard() {
  const navigate = useNavigate();
  const [stores, setStores] = useState<StoreResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [name, setName] = useState("");
  const [businessCategory, setBusinessCategory] = useState<BusinessCategory>("CAFE");
  const [operationType, setOperationType] = useState<OperationType>("FIXED");
  const [address, setAddress] = useState("");
  const [businessArea, setBusinessArea] = useState("");

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

  function handleCategoryChange(category: BusinessCategory) {
    setBusinessCategory(category);
    setOperationType(defaultOperationType(category));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const store = await createStore({
        name,
        businessCategory,
        operationType,
        address: operationType === "FIXED" ? address : undefined,
        businessArea: operationType === "MOBILE" ? businessArea : undefined,
        snsLinks: [],
      });
      setStores((prev) => [...prev, store]);
      setName("");
      setAddress("");
      setBusinessArea("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  }

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
          <li key={store.id} className="rounded border p-3">
            <span className="font-semibold">{store.name}</span>
            <span className="ml-2 text-sm text-gray-500">
              {BUSINESS_CATEGORY_LABELS[store.businessCategory]} /{" "}
              {OPERATION_TYPE_LABELS[store.operationType]}
            </span>
          </li>
        ))}
        {stores.length === 0 && <li className="text-gray-500">まだ店舗がありません</li>}
      </ul>

      <h2 className="mb-4 text-xl font-bold">店舗を作成</h2>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm font-medium">店名</label>
          <input
            required
            maxLength={50}
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded border p-2"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium">業種</label>
          <select
            value={businessCategory}
            onChange={(e) => handleCategoryChange(e.target.value as BusinessCategory)}
            className="w-full rounded border p-2"
          >
            {Object.entries(BUSINESS_CATEGORY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium">業態区分</label>
          <select
            value={operationType}
            onChange={(e) => setOperationType(e.target.value as OperationType)}
            className="w-full rounded border p-2"
          >
            {Object.entries(OPERATION_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        {operationType === "FIXED" ? (
          <div>
            <label className="mb-1 block text-sm font-medium">所在地（住所）</label>
            <input
              required
              maxLength={200}
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              className="w-full rounded border p-2"
            />
          </div>
        ) : (
          <div>
            <label className="mb-1 block text-sm font-medium">出店エリア</label>
            <input
              required
              maxLength={200}
              value={businessArea}
              onChange={(e) => setBusinessArea(e.target.value)}
              className="w-full rounded border p-2"
            />
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-black px-4 py-2 text-white disabled:opacity-50"
        >
          {submitting ? "作成中..." : "作成する"}
        </button>
      </form>
    </main>
  );
}
