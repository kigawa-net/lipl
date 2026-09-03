import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import {
  createMenuItem,
  createStore,
  deleteMenuItem,
  deletePhoto,
  getKaftBaseUrl,
  listMenuItems,
  listPhotos,
  photoUrl,
  reorderMenuItems,
  reorderPhotos,
  setMenuItemPhoto,
  setStorePublished,
  uploadPhoto,
  type BusinessCategory,
  type MenuItemResponse,
  type OperationType,
  type PhotoResponse,
  type SnsLinkInput,
  type SnsPlatform,
} from "~/lib/api";
import {
  BUSINESS_CATEGORY_LABELS,
  defaultOperationType,
  OPERATION_TYPE_LABELS,
  SNS_PLATFORM_LABELS,
  SNS_PLATFORMS,
} from "~/lib/labels";
import { isAuthenticated } from "~/lib/oidc";

const STEP_LABELS = ["基本情報", "営業形態", "SNS", "メニュー", "写真", "公開"];
const PHOTO_LIMIT = 15;
const MAX_PHOTO_SIZE = 10 * 1024 * 1024;
const ALLOWED_PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];

// ページをリロードしても入力内容が消えないよう、進行中のウィザードの状態を
// sessionStorageに保存する（タブを閉じると破棄される。メニュー・写真は
// 追加時点でサーバーに保存済みのため、ここには含めずstoreIdから再取得する）。
const DRAFT_KEY = "lipl.wizard.draft";

interface WizardDraft {
  stepIndex: number;
  name: string;
  businessCategory: BusinessCategory;
  operationType: OperationType;
  address: string;
  businessArea: string;
  businessHours: string;
  phone: string;
  snsUrls: Record<SnsPlatform, string>;
  storeId: number | null;
  slug: string | null;
}

function loadDraft(): WizardDraft | null {
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY);
    return raw ? (JSON.parse(raw) as WizardDraft) : null;
  } catch {
    return null;
  }
}

function saveDraft(draft: WizardDraft): void {
  try {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  } catch {
    // プライベートブラウジング等でsessionStorageが使えない場合は諦める
  }
}

function clearDraft(): void {
  try {
    sessionStorage.removeItem(DRAFT_KEY);
  } catch {
    // noop
  }
}

function ChevronIcon() {
  return (
    <svg className="field-select-chevron" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path
        fillRule="evenodd"
        d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z"
        clipRule="evenodd"
      />
    </svg>
  );
}

export default function StoreWizard() {
  const navigate = useNavigate();
  const [stepIndex, setStepIndex] = useState(0);

  // 基本情報〜SNSリンク（店舗作成前はローカル状態のみ）
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
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // 店舗作成後
  const [storeId, setStoreId] = useState<number | null>(null);
  const [slug, setSlug] = useState<string | null>(null);

  // メニュー
  const [menuItems, setMenuItems] = useState<MenuItemResponse[]>([]);
  const [menuName, setMenuName] = useState("");
  const [menuPrice, setMenuPrice] = useState("");
  const [menuDescription, setMenuDescription] = useState("");
  const [menuSubmitting, setMenuSubmitting] = useState(false);
  const [menuError, setMenuError] = useState<string | null>(null);
  const [menuPhotoSavingId, setMenuPhotoSavingId] = useState<number | null>(null);
  const [menuPhotoPickerId, setMenuPhotoPickerId] = useState<number | null>(null);

  // 写真
  const [photos, setPhotos] = useState<PhotoResponse[]>([]);
  const [kaftBaseUrl, setKaftBaseUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 公開
  const [publishing, setPublishing] = useState(false);
  const [publishError, setPublishError] = useState<string | null>(null);
  const [published, setPublished] = useState(false);

  const [restored, setRestored] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) {
      navigate("/login", { replace: true });
      return;
    }

    const draft = loadDraft();
    if (draft) {
      setStepIndex(draft.stepIndex);
      setName(draft.name);
      setBusinessCategory(draft.businessCategory);
      setOperationType(draft.operationType);
      setAddress(draft.address);
      setBusinessArea(draft.businessArea);
      setBusinessHours(draft.businessHours);
      setPhone(draft.phone);
      setSnsUrls(draft.snsUrls);
      setStoreId(draft.storeId);
      setSlug(draft.slug);

      if (draft.storeId !== null) {
        const id = draft.storeId;
        listMenuItems(id).then(setMenuItems).catch(() => {});
        listPhotos(id).then(setPhotos).catch(() => {});
      }
    }
    setRestored(true);
  }, [navigate]);

  // 復元処理が終わるまでは保存しない（空の初期状態で保存済みの下書きを
  // 上書きしてしまうのを防ぐため）。
  useEffect(() => {
    if (!restored) return;
    saveDraft({
      stepIndex,
      name,
      businessCategory,
      operationType,
      address,
      businessArea,
      businessHours,
      phone,
      snsUrls,
      storeId,
      slug,
    });
  }, [
    restored,
    stepIndex,
    name,
    businessCategory,
    operationType,
    address,
    businessArea,
    businessHours,
    phone,
    snsUrls,
    storeId,
    slug,
  ]);

  useEffect(() => {
    // メニュー（写真サムネイル表示）・写真ステップの両方で必要なので、
    // storeId確定後（=ステップ3以降に入れる状態になった時点で）取得しておく。
    if (storeId !== null && kaftBaseUrl === null) {
      getKaftBaseUrl()
        .then(setKaftBaseUrl)
        .catch((e: Error) => setPhotoError(e.message));
    }
  }, [storeId, kaftBaseUrl]);

  function handleCategoryChange(category: BusinessCategory) {
    setBusinessCategory(category);
    setOperationType(defaultOperationType(category));
  }

  function goBack() {
    setStepIndex((i) => Math.max(0, i - 1));
  }

  async function handleBasicNext(e: React.FormEvent) {
    e.preventDefault();
    setStepIndex(1);
  }

  async function handleOperationNext(e: React.FormEvent) {
    e.preventDefault();
    setStepIndex(2);
  }

  async function handleCreateStore(e: React.FormEvent) {
    e.preventDefault();
    if (storeId !== null) {
      // 一度作成済みなら再作成せず進むだけ（「戻る」で SNS ステップに戻ってきた場合）
      setStepIndex(3);
      return;
    }

    setCreating(true);
    setCreateError(null);
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
      setStoreId(store.id);
      setSlug(store.slug);
      setStepIndex(3);
    } catch (e) {
      setCreateError((e as Error).message);
    } finally {
      setCreating(false);
    }
  }

  async function handleAddMenuItem(e: React.FormEvent) {
    e.preventDefault();
    if (storeId === null) return;
    setMenuSubmitting(true);
    setMenuError(null);
    try {
      const item = await createMenuItem(storeId, {
        name: menuName,
        price: menuPrice ? Number(menuPrice) : undefined,
        description: menuDescription || undefined,
      });
      setMenuItems((prev) => [...prev, item]);
      setMenuName("");
      setMenuPrice("");
      setMenuDescription("");
    } catch (e) {
      setMenuError((e as Error).message);
    } finally {
      setMenuSubmitting(false);
    }
  }

  async function handleDeleteMenuItem(menuItemId: number) {
    if (storeId === null) return;
    setMenuError(null);
    try {
      await deleteMenuItem(storeId, menuItemId);
      setMenuItems((prev) => prev.filter((item) => item.id !== menuItemId));
    } catch (e) {
      setMenuError((e as Error).message);
    }
  }

  async function handleMoveMenuItem(index: number, direction: -1 | 1) {
    if (storeId === null) return;
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= menuItems.length) return;

    const reordered = [...menuItems];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    setMenuItems(reordered);

    try {
      await reorderMenuItems(
        storeId,
        reordered.map((item) => item.id),
      );
    } catch (e) {
      setMenuError((e as Error).message);
    }
  }

  async function handleMenuPhotoPick(menuItemId: number, photoId: number | null) {
    if (storeId === null) return;
    setMenuError(null);
    setMenuPhotoSavingId(menuItemId);
    try {
      const updated = await setMenuItemPhoto(storeId, menuItemId, photoId);
      setMenuItems((prev) => prev.map((item) => (item.id === menuItemId ? updated : item)));
      setMenuPhotoPickerId(null);
    } catch (e) {
      setMenuError((e as Error).message);
    } finally {
      setMenuPhotoSavingId(null);
    }
  }

  async function handlePhotoSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file || storeId === null) return;

    setPhotoError(null);

    if (photos.length >= PHOTO_LIMIT) {
      setPhotoError(`写真は最大${PHOTO_LIMIT}枚までです`);
      return;
    }
    if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
      setPhotoError("JPEG・PNG・WebP形式の画像のみアップロードできます");
      return;
    }
    if (file.size > MAX_PHOTO_SIZE) {
      setPhotoError("画像サイズは10MB以下にしてください");
      return;
    }

    setUploading(true);
    try {
      const photo = await uploadPhoto(storeId, file);
      setPhotos((prev) => [...prev, photo]);
    } catch (e) {
      setPhotoError((e as Error).message);
    } finally {
      setUploading(false);
    }
  }

  async function handleDeletePhoto(photoId: number) {
    if (storeId === null) return;
    setPhotoError(null);
    try {
      await deletePhoto(storeId, photoId);
      setPhotos((prev) => prev.filter((photo) => photo.id !== photoId));
    } catch (e) {
      setPhotoError((e as Error).message);
    }
  }

  async function handleMovePhoto(index: number, direction: -1 | 1) {
    if (storeId === null) return;
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= photos.length) return;

    const reordered = [...photos];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    setPhotos(reordered);

    try {
      await reorderPhotos(
        storeId,
        reordered.map((photo) => photo.id),
      );
    } catch (e) {
      setPhotoError((e as Error).message);
    }
  }

  async function handlePublish() {
    if (storeId === null) return;
    setPublishing(true);
    setPublishError(null);
    try {
      await setStorePublished(storeId, true);
      clearDraft();
      setPublished(true);
    } catch (e) {
      setPublishError((e as Error).message);
    } finally {
      setPublishing(false);
    }
  }

  if (published && slug) {
    return (
      <main className="mx-auto flex min-h-screen max-w-lg flex-col items-center justify-center gap-4 p-8 text-center">
        <div className="text-4xl">🎉</div>
        <h1 className="text-2xl font-bold dark:text-stone-100">公開しました！</h1>
        <p className="text-gray-600 dark:text-stone-400">
          「{name}」のページが公開されました。
          <br />
          お客様にはこちらのリンクを共有できます。
        </p>
        <a
          href={`/p/${slug}`}
          target="_blank"
          rel="noreferrer"
          className="w-full rounded-lg bg-amber-900 px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 dark:bg-amber-700 dark:hover:bg-amber-600"
        >
          公開ページを見る
        </a>
        <a
          href="/dashboard"
          className="text-sm text-amber-800 underline hover:text-amber-900 dark:text-amber-500 dark:hover:text-amber-400"
        >
          ダッシュボードに戻る
        </a>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold dark:text-stone-100">店舗を登録</h1>
        <a href="/dashboard" className="text-sm text-gray-500 underline dark:text-stone-500">
          あとで続ける
        </a>
      </div>

      <ol className="mb-8 flex flex-wrap gap-x-1 gap-y-2 text-xs font-medium">
        {STEP_LABELS.map((label, index) => (
          <li key={label} className="flex items-center gap-1">
            <span
              className={
                index === stepIndex
                  ? "flex h-6 w-6 items-center justify-center rounded-full bg-amber-900 text-white dark:bg-amber-600"
                  : index < stepIndex
                    ? "flex h-6 w-6 items-center justify-center rounded-full bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-400"
                    : "flex h-6 w-6 items-center justify-center rounded-full bg-gray-100 text-gray-400 dark:bg-stone-800 dark:text-stone-500"
              }
            >
              {index + 1}
            </span>
            <span
              className={
                index === stepIndex
                  ? "text-gray-900 dark:text-stone-100"
                  : "text-gray-400 dark:text-stone-600"
              }
            >
              {label}
            </span>
            {index < STEP_LABELS.length - 1 && (
              <span className="mx-1 text-gray-300 dark:text-stone-700">―</span>
            )}
          </li>
        ))}
      </ol>

      {stepIndex === 0 && (
        <form onSubmit={handleBasicNext} className="form-card">
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
                  autoFocus
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
                  <ChevronIcon />
                </div>
              </div>
            </div>
          </div>

          <button
            type="submit"
            className="mt-7 w-full rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 sm:w-auto sm:px-6 dark:bg-amber-700 dark:hover:bg-amber-600"
          >
            次へ
          </button>
        </form>
      )}

      {stepIndex === 1 && (
        <form onSubmit={handleOperationNext} className="form-card">
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
                  <ChevronIcon />
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
                    autoFocus
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
                    autoFocus
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

          <div className="mt-7 flex gap-3">
            <button
              type="button"
              onClick={goBack}
              className="rounded-lg border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-600 transition-colors hover:border-gray-300 dark:border-stone-700 dark:text-stone-300 dark:hover:border-stone-600"
            >
              戻る
            </button>
            <button
              type="submit"
              className="rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 sm:px-6 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              次へ
            </button>
          </div>
        </form>
      )}

      {stepIndex === 2 && (
        <form onSubmit={handleCreateStore} className="form-card">
          <div className="form-section">
            <p className="form-section-title">SNSリンク</p>
            <p className="field-hint mb-4">お店のSNSがあれば入力してください（あとから追加できます）</p>
            <div className="space-y-2">
              {SNS_PLATFORMS.map((platform) => (
                <label key={platform} className="sns-row">
                  <span className="sns-badge">{SNS_PLATFORM_LABELS[platform]}</span>
                  <input
                    type="url"
                    maxLength={500}
                    value={snsUrls[platform]}
                    onChange={(e) => setSnsUrls((prev) => ({ ...prev, [platform]: e.target.value }))}
                    placeholder="https://..."
                    className="sns-input"
                  />
                </label>
              ))}
            </div>
          </div>

          {createError && <p className="mt-4 text-sm text-red-600 dark:text-red-400">{createError}</p>}

          <div className="mt-7 flex gap-3">
            <button
              type="button"
              onClick={goBack}
              disabled={creating}
              className="rounded-lg border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-600 transition-colors hover:border-gray-300 disabled:opacity-50 dark:border-stone-700 dark:text-stone-300 dark:hover:border-stone-600"
            >
              戻る
            </button>
            <button
              type="submit"
              disabled={creating}
              className="rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 disabled:opacity-50 sm:px-6 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              {creating ? "作成中..." : "次へ"}
            </button>
          </div>
        </form>
      )}

      {stepIndex === 3 && storeId !== null && (
        <div className="form-card">
          <div className="form-section">
            <p className="form-section-title">メニュー</p>
            <p className="field-hint mb-4">
              代表的なメニューをいくつか登録しましょう（あとからいつでも追加・編集できます）
            </p>

            {menuError && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{menuError}</p>}

            <ul className="mb-5 space-y-2">
              {menuItems.map((item, index) => {
                const itemPhoto = photos.find((photo) => photo.id === item.photoId);
                const pickerOpen = menuPhotoPickerId === item.id;
                return (
                  <li key={item.id} className="rounded-lg border border-gray-200 p-3 dark:border-stone-700">
                    <div className="flex items-center justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <button
                          type="button"
                          onClick={() => setMenuPhotoPickerId(pickerOpen ? null : item.id)}
                          disabled={photos.length === 0 || menuPhotoSavingId === item.id}
                          className="flex h-12 w-12 shrink-0 items-center justify-center rounded border border-dashed text-[0.6rem] text-gray-400 transition-colors hover:border-amber-500 hover:text-amber-700 disabled:opacity-50 dark:border-stone-700 dark:text-stone-600 dark:hover:border-amber-600 dark:hover:text-amber-500"
                        >
                          {itemPhoto && kaftBaseUrl ? (
                            <img
                              src={photoUrl(kaftBaseUrl, itemPhoto)}
                              alt={item.name}
                              className="h-full w-full rounded object-cover"
                            />
                          ) : menuPhotoSavingId === item.id ? (
                            "..."
                          ) : (
                            "写真"
                          )}
                        </button>
                        <div className="min-w-0">
                          <span className="font-semibold dark:text-stone-100">{item.name}</span>
                          {item.price != null && (
                            <span className="ml-2 text-sm text-gray-500 dark:text-stone-400">
                              ¥{item.price.toLocaleString()}
                            </span>
                          )}
                          {photos.length === 0 && (
                            <p className="text-xs text-gray-400 dark:text-stone-500">
                              写真をアップロードすると選択できます
                            </p>
                          )}
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-1">
                        <button
                          type="button"
                          onClick={() => handleMoveMenuItem(index, -1)}
                          disabled={index === 0}
                          className="rounded border px-2 py-1 text-xs transition-colors hover:border-amber-700 hover:text-amber-800 disabled:opacity-30 dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
                        >
                          ↑
                        </button>
                        <button
                          type="button"
                          onClick={() => handleMoveMenuItem(index, 1)}
                          disabled={index === menuItems.length - 1}
                          className="rounded border px-2 py-1 text-xs transition-colors hover:border-amber-700 hover:text-amber-800 disabled:opacity-30 dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
                        >
                          ↓
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDeleteMenuItem(item.id)}
                          className="rounded border px-2 py-1 text-xs text-red-600 dark:border-stone-700 dark:text-red-400"
                        >
                          削除
                        </button>
                      </div>
                    </div>
                    {pickerOpen && kaftBaseUrl && (
                      <div className="mt-3 flex flex-wrap gap-2 border-t pt-3 dark:border-stone-700">
                        <button
                          type="button"
                          onClick={() => handleMenuPhotoPick(item.id, null)}
                          className="flex h-12 w-12 items-center justify-center rounded border border-dashed text-[0.55rem] text-gray-400 transition-colors hover:border-amber-500 hover:text-amber-700 dark:border-stone-700 dark:text-stone-600 dark:hover:border-amber-600 dark:hover:text-amber-500"
                        >
                          写真なし
                        </button>
                        {photos.map((photo) => (
                          <button
                            key={photo.id}
                            type="button"
                            onClick={() => handleMenuPhotoPick(item.id, photo.id)}
                            className={`h-12 w-12 shrink-0 overflow-hidden rounded border-2 transition-colors ${
                              item.photoId === photo.id
                                ? "border-amber-700 dark:border-amber-500"
                                : "border-transparent hover:border-amber-300 dark:hover:border-amber-700"
                            }`}
                          >
                            <img
                              src={photoUrl(kaftBaseUrl, photo)}
                              alt={photo.filename}
                              className="h-full w-full object-cover"
                            />
                          </button>
                        ))}
                      </div>
                    )}
                  </li>
                );
              })}
              {menuItems.length === 0 && (
                <li className="text-sm text-gray-400 dark:text-stone-500">まだメニューがありません</li>
              )}
            </ul>

            <form
              onSubmit={handleAddMenuItem}
              className="space-y-3 border-t border-amber-100 pt-5 dark:border-stone-800"
            >
              <div className="field">
                <label className="field-label">品名</label>
                <input
                  required
                  maxLength={50}
                  value={menuName}
                  onChange={(e) => setMenuName(e.target.value)}
                  placeholder="例: 自家焙煎ブレンド"
                  className="field-input"
                />
              </div>
              <div className="field">
                <label className="field-label">価格</label>
                <div className="relative">
                  <span className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-sm text-gray-400 dark:text-stone-500">
                    ¥
                  </span>
                  <input
                    type="number"
                    min={0}
                    value={menuPrice}
                    onChange={(e) => setMenuPrice(e.target.value)}
                    placeholder="0"
                    className="field-input pl-7"
                  />
                </div>
              </div>
              <div className="field">
                <label className="field-label">説明文</label>
                <textarea
                  rows={2}
                  maxLength={200}
                  value={menuDescription}
                  onChange={(e) => setMenuDescription(e.target.value)}
                  className="field-textarea field-input"
                />
              </div>
              <button
                type="submit"
                disabled={menuSubmitting}
                className="rounded-lg border border-amber-700 px-4 py-2 text-sm font-semibold text-amber-800 transition-colors hover:bg-amber-50 disabled:opacity-50 dark:border-amber-600 dark:text-amber-500 dark:hover:bg-amber-900/30"
              >
                {menuSubmitting ? "追加中..." : "+ メニューを追加"}
              </button>
            </form>
          </div>

          <div className="mt-7 flex gap-3">
            <button
              type="button"
              onClick={() => setStepIndex(4)}
              className="rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 sm:px-6 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              {menuItems.length === 0 ? "スキップして次へ" : "次へ"}
            </button>
          </div>
        </div>
      )}

      {stepIndex === 4 && storeId !== null && (
        <div className="form-card">
          <div className="form-section">
            <p className="form-section-title">写真</p>
            <p className="field-hint mb-4">お店や料理の写真を追加すると、公開ページが魅力的になります</p>

            {photoError && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{photoError}</p>}

            <div className="mb-4 grid grid-cols-3 gap-2">
              {photos.map((photo, index) => (
                <div key={photo.id} className="space-y-1">
                  {kaftBaseUrl && (
                    <img
                      src={photoUrl(kaftBaseUrl, photo)}
                      alt={photo.filename}
                      className="aspect-square w-full rounded border object-cover dark:border-stone-700"
                    />
                  )}
                  <div className="flex items-center justify-center gap-1">
                    <button
                      type="button"
                      onClick={() => handleMovePhoto(index, -1)}
                      disabled={index === 0}
                      className="rounded border px-2 py-1 text-xs transition-colors hover:border-amber-700 hover:text-amber-800 disabled:opacity-30 dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      onClick={() => handleMovePhoto(index, 1)}
                      disabled={index === photos.length - 1}
                      className="rounded border px-2 py-1 text-xs transition-colors hover:border-amber-700 hover:text-amber-800 disabled:opacity-30 dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
                    >
                      ↓
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDeletePhoto(photo.id)}
                      className="rounded border px-2 py-1 text-xs text-red-600 dark:border-stone-700 dark:text-red-400"
                    >
                      削除
                    </button>
                  </div>
                </div>
              ))}
            </div>
            {photos.length === 0 && (
              <p className="mb-4 text-sm text-gray-400 dark:text-stone-500">まだ写真がありません</p>
            )}

            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={handlePhotoSelect}
              disabled={uploading || photos.length >= PHOTO_LIMIT}
              className="text-sm file:mr-3 file:rounded file:border-0 file:bg-amber-100 file:px-3 file:py-1.5 file:text-amber-900 file:transition-colors hover:file:bg-amber-200 dark:text-stone-300 dark:file:bg-amber-900/40 dark:file:text-amber-400 dark:hover:file:bg-amber-900/60"
            />
            <p className="mt-1 text-xs text-gray-500 dark:text-stone-500">
              {uploading
                ? "アップロード中..."
                : `JPEG・PNG・WebP、10MBまで（${photos.length}/${PHOTO_LIMIT}枚）`}
            </p>
          </div>

          <div className="mt-7 flex gap-3">
            <button
              type="button"
              onClick={() => setStepIndex(5)}
              className="rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 sm:px-6 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              {photos.length === 0 ? "スキップして次へ" : "次へ"}
            </button>
          </div>
        </div>
      )}

      {stepIndex === 5 && storeId !== null && (
        <div className="form-card">
          <div className="form-section">
            <p className="form-section-title">確認・公開</p>
            <p className="field-hint mb-5">内容を確認して、問題なければ公開しましょう。公開後もいつでも編集できます。</p>

            <dl className="space-y-3 text-sm">
              <div className="flex justify-between border-b border-amber-100 pb-2 dark:border-stone-800">
                <dt className="text-gray-500 dark:text-stone-400">店名</dt>
                <dd className="font-semibold dark:text-stone-100">{name}</dd>
              </div>
              <div className="flex justify-between border-b border-amber-100 pb-2 dark:border-stone-800">
                <dt className="text-gray-500 dark:text-stone-400">業種</dt>
                <dd className="dark:text-stone-200">{BUSINESS_CATEGORY_LABELS[businessCategory]}</dd>
              </div>
              <div className="flex justify-between border-b border-amber-100 pb-2 dark:border-stone-800">
                <dt className="text-gray-500 dark:text-stone-400">
                  {operationType === "FIXED" ? "所在地" : "出店エリア"}
                </dt>
                <dd className="dark:text-stone-200">{operationType === "FIXED" ? address : businessArea}</dd>
              </div>
              <div className="flex justify-between border-b border-amber-100 pb-2 dark:border-stone-800">
                <dt className="text-gray-500 dark:text-stone-400">メニュー</dt>
                <dd className="dark:text-stone-200">{menuItems.length}品</dd>
              </div>
              <div className="flex justify-between pb-2">
                <dt className="text-gray-500 dark:text-stone-400">写真</dt>
                <dd className="dark:text-stone-200">{photos.length}枚</dd>
              </div>
            </dl>

            {publishError && <p className="mt-4 text-sm text-red-600 dark:text-red-400">{publishError}</p>}
          </div>

          <div className="mt-7 flex gap-3">
            <button
              type="button"
              onClick={goBack}
              disabled={publishing}
              className="rounded-lg border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-600 transition-colors hover:border-gray-300 disabled:opacity-50 dark:border-stone-700 dark:text-stone-300 dark:hover:border-stone-600"
            >
              戻る
            </button>
            <button
              type="button"
              onClick={handlePublish}
              disabled={publishing}
              className="rounded-lg bg-amber-900 px-6 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 disabled:opacity-50 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              {publishing ? "公開中..." : "公開する"}
            </button>
          </div>
        </div>
      )}
    </main>
  );
}
