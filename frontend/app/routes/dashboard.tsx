import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import {
  createStore,
  listStores,
  type BusinessCategory,
  type OperationType,
  type SnsLinkInput,
  type SnsPlatform,
  type StoreResponse,
} from "~/lib/api";
import { isAuthenticated } from "~/lib/oidc";

const SNS_PLATFORM_LABELS: Record<SnsPlatform, string> = {
  INSTAGRAM: "Instagram",
  X: "X",
  FACEBOOK: "Facebook",
  LINE: "LINE",
  TIKTOK: "TikTok",
  YOUTUBE: "YouTube",
};

const SNS_PLATFORMS = Object.keys(SNS_PLATFORM_LABELS) as SnsPlatform[];

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
  const [businessHours, setBusinessHours] = useState("");
  const [phone, setPhone] = useState("");
  const [snsUrls, setSnsUrls] = useState<Record<SnsPlatform, string>>({
    INSTAGRAM: "",
    X: "",
    FACEBOOK: "",
    LINE: "",
    TIKTOK: "",
    YOUTUBE: "",
  });

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
      const snsLinks: SnsLinkInput[] = SNS_PLATFORMS.filter((platform) => snsUrls[platform].trim() !== "").map(
        (platform) => ({ platform, url: snsUrls[platform].trim() }),
      );

      const store = await createStore({
        name,
        businessCategory,
        operationType,
        address: operationType === "FIXED" ? address : undefined,
        businessArea: operationType === "MOBILE" ? businessArea : undefined,
        businessHours: businessHours || undefined,
        phone: phone || undefined,
        snsLinks,
      });
      setStores((prev) => [...prev, store]);
      setName("");
      setAddress("");
      setBusinessArea("");
      setBusinessHours("");
      setPhone("");
      setSnsUrls({ INSTAGRAM: "", X: "", FACEBOOK: "", LINE: "", TIKTOK: "", YOUTUBE: "" });
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
            <a href={`/stores/${store.id}`} className="ml-2 text-sm text-amber-800 underline hover:text-amber-900">
              メニュー管理
            </a>
          </li>
        ))}
        {stores.length === 0 && <li className="text-gray-500">まだ店舗がありません</li>}
      </ul>

      <h2 className="mb-4 text-xl font-bold">店舗を作成</h2>
      <form onSubmit={handleSubmit} className="form-card">
        <div className="form-section">
          <p className="form-section-title">基本情報</p>

          <div className="space-y-5">
            <div className="field">
              <label className="field-label">
                <span>店名</span>
                <span className="flex items-center gap-2">
                  <span className="field-tag-required">必須</span>
                  <span className="field-count">{name.length}/50</span>
                </span>
              </label>
              <input
                required
                maxLength={50}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="例: 喫茶ひだまり"
                className="field-input"
              />
            </div>

            <div className="field">
              <label className="field-label">業種</label>
              <div className="field-select-wrap">
                <select
                  value={businessCategory}
                  onChange={(e) => handleCategoryChange(e.target.value as BusinessCategory)}
                  className="field-select"
                >
                  {Object.entries(BUSINESS_CATEGORY_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <svg className="field-select-chevron" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clipRule="evenodd" />
                </svg>
              </div>
            </div>
          </div>
        </div>

        <div className="form-section">
          <p className="form-section-title">営業形態</p>

          <div className="space-y-5">
            <div className="field">
              <label className="field-label">業態区分</label>
              <div className="field-select-wrap">
                <select
                  value={operationType}
                  onChange={(e) => setOperationType(e.target.value as OperationType)}
                  className="field-select"
                >
                  {Object.entries(OPERATION_TYPE_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <svg className="field-select-chevron" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fillRule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clipRule="evenodd" />
                </svg>
              </div>
            </div>

            {operationType === "FIXED" ? (
              <div className="field">
                <label className="field-label">
                  <span>所在地（住所）</span>
                  <span className="flex items-center gap-2">
                    <span className="field-tag-required">必須</span>
                    <span className="field-count">{address.length}/200</span>
                  </span>
                </label>
                <input
                  required
                  maxLength={200}
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="例: 東京都渋谷区〇〇1-2-3"
                  className="field-input"
                />
              </div>
            ) : (
              <div className="field">
                <label className="field-label">
                  <span>出店エリア</span>
                  <span className="flex items-center gap-2">
                    <span className="field-tag-required">必須</span>
                    <span className="field-count">{businessArea.length}/200</span>
                  </span>
                </label>
                <input
                  required
                  maxLength={200}
                  value={businessArea}
                  onChange={(e) => setBusinessArea(e.target.value)}
                  placeholder="例: 都内近郊のマルシェ・イベント中心"
                  className="field-input"
                />
              </div>
            )}

            <div className="field">
              <label className="field-label">
                <span>営業時間</span>
                <span className="field-tag">任意</span>
              </label>
              <input
                maxLength={200}
                value={businessHours}
                onChange={(e) => setBusinessHours(e.target.value)}
                placeholder="例: 平日11:00-22:00 / 土日祝10:00-22:00"
                className="field-input"
              />
            </div>

            <div className="field">
              <label className="field-label">
                <span>電話番号</span>
                <span className="field-tag">任意</span>
              </label>
              <input
                maxLength={20}
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="例: 03-1234-5678"
                className="field-input"
              />
            </div>
          </div>
        </div>

        <div className="form-section">
          <p className="form-section-title">SNSリンク</p>
          <div className="space-y-2">
            {SNS_PLATFORMS.map((platform) => (
              <label key={platform} className="sns-row">
                <span className="sns-badge">{SNS_PLATFORM_LABELS[platform]}</span>
                <input
                  type="url"
                  maxLength={500}
                  value={snsUrls[platform]}
                  onChange={(e) =>
                    setSnsUrls((prev) => ({ ...prev, [platform]: e.target.value }))
                  }
                  placeholder="https://..."
                  className="sns-input"
                />
              </label>
            ))}
          </div>
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="mt-7 w-full rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 disabled:opacity-50 sm:w-auto sm:px-6"
        >
          {submitting ? "作成中..." : "作成する"}
        </button>
      </form>
    </main>
  );
}
