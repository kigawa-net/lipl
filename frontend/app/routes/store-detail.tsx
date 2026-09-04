import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
  createMenuItem,
  deleteMenuItem,
  deletePhoto,
  getKaftBaseUrl,
  getStore,
  listMenuItems,
  listPhotos,
  photoUrl,
  reorderMenuItems,
  reorderPhotos,
  setMenuItemPhoto,
  updateMenuItem,
  updateStore,
  uploadPhoto,
  type BusinessCategory,
  type MenuItemResponse,
  type OperationType,
  type PhotoResponse,
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

const PHOTO_LIMIT = 15;
const MAX_PHOTO_SIZE = 10 * 1024 * 1024;
const ALLOWED_PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];

export default function StoreDetail() {
  const { storeId } = useParams();
  const navigate = useNavigate();
  const [menuItems, setMenuItems] = useState<MenuItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [description, setDescription] = useState("");

  const [storeName, setStoreName] = useState("");
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
  const [storeLoading, setStoreLoading] = useState(true);
  const [storeError, setStoreError] = useState<string | null>(null);
  const [storeSaving, setStoreSaving] = useState(false);
  const [storeSaved, setStoreSaved] = useState(false);

  const [photos, setPhotos] = useState<PhotoResponse[]>([]);
  const [kaftBaseUrl, setKaftBaseUrl] = useState<string | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [menuPhotoSavingId, setMenuPhotoSavingId] = useState<number | null>(null);
  const [menuPhotoPickerId, setMenuPhotoPickerId] = useState<number | null>(null);

  const [editingMenuItemId, setEditingMenuItemId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [editPrice, setEditPrice] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editSaving, setEditSaving] = useState(false);

  const numericStoreId = Number(storeId);

  useEffect(() => {
    if (!isAuthenticated()) {
      navigate("/login", { replace: true });
      return;
    }
    getStore(numericStoreId)
      .then((s) => {
        setStoreName(s.name);
        setBusinessCategory(s.businessCategory);
        setOperationType(s.operationType);
        setAddress(s.address ?? "");
        setBusinessArea(s.businessArea ?? "");
        setBusinessHours(s.businessHours ?? "");
        setPhone(s.phone ?? "");
        setSnsUrls((prev) => {
          const next = { ...prev };
          s.snsLinks.forEach((link) => {
            next[link.platform] = link.url;
          });
          return next;
        });
      })
      .catch((e: Error) => setStoreError(e.message))
      .finally(() => setStoreLoading(false));

    listMenuItems(numericStoreId)
      .then(setMenuItems)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));

    Promise.all([listPhotos(numericStoreId), getKaftBaseUrl()])
      .then(([photoList, baseUrl]) => {
        setPhotos(photoList);
        setKaftBaseUrl(baseUrl);
      })
      .catch((e: Error) => setPhotoError(e.message));
  }, [numericStoreId, navigate]);

  function handleCategoryChange(category: BusinessCategory) {
    setBusinessCategory(category);
    setOperationType(defaultOperationType(category));
  }

  async function handleStoreSubmit(e: React.FormEvent) {
    e.preventDefault();
    setStoreSaving(true);
    setStoreError(null);
    setStoreSaved(false);
    try {
      await updateStore(numericStoreId, {
        name: storeName,
        businessCategory,
        operationType,
        address: operationType === "FIXED" ? address : undefined,
        businessArea: operationType === "MOBILE" ? businessArea : undefined,
        businessHours: businessHours || undefined,
        phone: phone || undefined,
        snsLinks: SNS_PLATFORMS.filter((platform) => snsUrls[platform].trim() !== "").map((platform) => ({
          platform,
          url: snsUrls[platform],
        })),
      });
      setStoreSaved(true);
    } catch (e) {
      setStoreError((e as Error).message);
    } finally {
      setStoreSaving(false);
    }
  }

  async function handlePhotoSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

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
      const photo = await uploadPhoto(numericStoreId, file);
      setPhotos((prev) => [...prev, photo]);
    } catch (e) {
      setPhotoError((e as Error).message);
    } finally {
      setUploading(false);
    }
  }

  async function handlePhotoDelete(photoId: number) {
    setPhotoError(null);
    try {
      await deletePhoto(numericStoreId, photoId);
      setPhotos((prev) => prev.filter((photo) => photo.id !== photoId));
    } catch (e) {
      setPhotoError((e as Error).message);
    }
  }

  async function handlePhotoMove(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= photos.length) return;

    const reordered = [...photos];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    setPhotos(reordered);

    try {
      await reorderPhotos(
        numericStoreId,
        reordered.map((photo) => photo.id),
      );
    } catch (e) {
      setPhotoError((e as Error).message);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const item = await createMenuItem(numericStoreId, {
        name,
        price: price ? Number(price) : undefined,
        description: description || undefined,
      });
      setMenuItems((prev) => [...prev, item]);
      setName("");
      setPrice("");
      setDescription("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(menuItemId: number) {
    setError(null);
    try {
      await deleteMenuItem(numericStoreId, menuItemId);
      setMenuItems((prev) => prev.filter((item) => item.id !== menuItemId));
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function handleMove(index: number, direction: -1 | 1) {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= menuItems.length) return;

    const reordered = [...menuItems];
    [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
    setMenuItems(reordered);

    try {
      await reorderMenuItems(
        numericStoreId,
        reordered.map((item) => item.id),
      );
    } catch (e) {
      setError((e as Error).message);
    }
  }

  function startEditMenuItem(item: MenuItemResponse) {
    setEditingMenuItemId(item.id);
    setEditName(item.name);
    setEditPrice(item.price != null ? String(item.price) : "");
    setEditDescription(item.description ?? "");
    setError(null);
  }

  function cancelEditMenuItem() {
    setEditingMenuItemId(null);
  }

  async function handleEditMenuItemSubmit(e: React.FormEvent, menuItemId: number) {
    e.preventDefault();
    setEditSaving(true);
    setError(null);
    try {
      const updated = await updateMenuItem(numericStoreId, menuItemId, {
        name: editName,
        price: editPrice ? Number(editPrice) : undefined,
        description: editDescription || undefined,
      });
      setMenuItems((prev) => prev.map((item) => (item.id === menuItemId ? updated : item)));
      setEditingMenuItemId(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setEditSaving(false);
    }
  }

  async function handleMenuPhotoPick(menuItemId: number, photoId: number | null) {
    setError(null);
    setMenuPhotoSavingId(menuItemId);
    try {
      const updated = await setMenuItemPhoto(numericStoreId, menuItemId, photoId);
      setMenuItems((prev) => prev.map((item) => (item.id === menuItemId ? updated : item)));
      setMenuPhotoPickerId(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setMenuPhotoSavingId(null);
    }
  }

  if (loading || storeLoading) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="dark:text-stone-300">読み込み中...</p>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-2xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold dark:text-stone-100">店舗管理</h1>
        <a href="/dashboard" className="text-sm text-gray-500 underline dark:text-stone-500">
          店舗一覧に戻る
        </a>
      </div>

      <h2 className="mb-4 text-xl font-bold dark:text-stone-100">店舗情報</h2>
      <form onSubmit={handleStoreSubmit} className="form-card mb-8">
        <div className="form-section">
          <div className="space-y-5">
            <div className="field">
              <label className="field-label">
                <span>店名</span>
                <span className="flex items-center gap-2">
                  <span className="field-tag-required">必須</span>
                  <span className="field-count">{storeName.length}/50</span>
                </span>
              </label>
              <input
                required
                maxLength={50}
                value={storeName}
                onChange={(e) => setStoreName(e.target.value)}
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
                <ChevronIcon />
              </div>
            </div>

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
                  onChange={(e) => setSnsUrls((prev) => ({ ...prev, [platform]: e.target.value }))}
                  placeholder="https://..."
                  className="sns-input"
                />
              </label>
            ))}
          </div>
        </div>

        {storeError && <p className="mt-4 text-sm text-red-600 dark:text-red-400">{storeError}</p>}
        {storeSaved && !storeError && (
          <p className="mt-4 text-sm text-amber-800 dark:text-amber-500">保存しました</p>
        )}

        <button
          type="submit"
          disabled={storeSaving}
          className="mt-6 w-full rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 disabled:opacity-50 sm:w-auto sm:px-6 dark:bg-amber-700 dark:hover:bg-amber-600"
        >
          {storeSaving ? "保存中..." : "店舗情報を保存"}
        </button>
      </form>

      <h2 className="mb-4 text-xl font-bold dark:text-stone-100">写真</h2>
      {photoError && <p className="mb-4 text-red-600 dark:text-red-400">{photoError}</p>}

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
                onClick={() => handlePhotoMove(index, -1)}
                disabled={index === 0}
                className="rounded border px-2 py-1 text-xs transition-colors hover:border-amber-700 hover:text-amber-800 disabled:opacity-30 disabled:hover:border-inherit disabled:hover:text-inherit dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
              >
                ↑
              </button>
              <button
                type="button"
                onClick={() => handlePhotoMove(index, 1)}
                disabled={index === photos.length - 1}
                className="rounded border px-2 py-1 text-xs transition-colors hover:border-amber-700 hover:text-amber-800 disabled:opacity-30 disabled:hover:border-inherit disabled:hover:text-inherit dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
              >
                ↓
              </button>
              <button
                type="button"
                onClick={() => handlePhotoDelete(photo.id)}
                className="rounded border px-2 py-1 text-xs text-red-600 dark:border-stone-700 dark:text-red-400"
              >
                削除
              </button>
            </div>
          </div>
        ))}
      </div>
      {photos.length === 0 && <p className="mb-4 text-gray-500 dark:text-stone-500">まだ写真がありません</p>}

      <div className="mb-8">
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

      <h2 className="mb-4 text-xl font-bold dark:text-stone-100">メニュー</h2>
      {error && <p className="mb-4 text-red-600 dark:text-red-400">{error}</p>}

      <ul className="mb-8 space-y-2">
        {menuItems.map((item, index) => {
          const itemPhoto = photos.find((photo) => photo.id === item.photoId);
          const pickerOpen = menuPhotoPickerId === item.id;
          const editing = editingMenuItemId === item.id;
          return (
            <li key={item.id} className="rounded border p-3 dark:border-stone-700">
              <div className="flex items-center justify-between gap-3">
                <div className="flex min-w-0 flex-1 items-center gap-3">
                  <button
                    type="button"
                    onClick={() => setMenuPhotoPickerId(pickerOpen ? null : item.id)}
                    disabled={photos.length === 0 || menuPhotoSavingId === item.id}
                    className="flex h-14 w-14 shrink-0 items-center justify-center rounded border border-dashed text-[0.65rem] text-gray-400 transition-colors hover:border-amber-500 hover:text-amber-700 disabled:opacity-50 dark:border-stone-700 dark:text-stone-600 dark:hover:border-amber-600 dark:hover:text-amber-500"
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
                  {editing ? (
                    <form
                      onSubmit={(e) => handleEditMenuItemSubmit(e, item.id)}
                      className="min-w-0 flex-1 space-y-2"
                    >
                      <input
                        required
                        maxLength={50}
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        placeholder="品名"
                        className="field-input"
                        autoFocus
                      />
                      <div className="relative">
                        <span className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-sm text-gray-400 dark:text-stone-500">
                          ¥
                        </span>
                        <input
                          type="number"
                          min={0}
                          value={editPrice}
                          onChange={(e) => setEditPrice(e.target.value)}
                          placeholder="0"
                          className="field-input pl-7"
                        />
                      </div>
                      <textarea
                        rows={2}
                        maxLength={200}
                        value={editDescription}
                        onChange={(e) => setEditDescription(e.target.value)}
                        placeholder="説明文"
                        className="field-textarea field-input"
                      />
                      <div className="flex gap-2">
                        <button
                          type="submit"
                          disabled={editSaving}
                          className="rounded border border-amber-800 bg-amber-900 px-3 py-1 text-xs font-semibold text-white transition-colors hover:bg-amber-800 disabled:opacity-50 dark:bg-amber-700 dark:hover:bg-amber-600"
                        >
                          {editSaving ? "保存中..." : "保存"}
                        </button>
                        <button
                          type="button"
                          onClick={cancelEditMenuItem}
                          disabled={editSaving}
                          className="rounded border px-3 py-1 text-xs dark:border-stone-700 dark:text-stone-300"
                        >
                          キャンセル
                        </button>
                      </div>
                    </form>
                  ) : (
                    <div className="min-w-0">
                      <span className="font-semibold dark:text-stone-100">{item.name}</span>
                      {item.price != null && (
                        <span className="ml-2 text-sm text-gray-500 dark:text-stone-400">
                          ¥{item.price.toLocaleString()}
                        </span>
                      )}
                      {item.description && (
                        <p className="truncate text-sm text-gray-500 dark:text-stone-400">{item.description}</p>
                      )}
                      {photos.length === 0 && (
                        <p className="text-xs text-gray-400 dark:text-stone-500">写真をアップロードすると選択できます</p>
                      )}
                    </div>
                  )}
                </div>
                {!editing && (
                  <div className="flex shrink-0 items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleMove(index, -1)}
                      disabled={index === 0}
                      className="rounded border px-2 py-1 text-sm transition-colors hover:border-amber-700 hover:text-amber-800 disabled:opacity-30 disabled:hover:border-inherit disabled:hover:text-inherit dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      onClick={() => handleMove(index, 1)}
                      disabled={index === menuItems.length - 1}
                      className="rounded border px-2 py-1 text-sm transition-colors hover:border-amber-700 hover:text-amber-800 disabled:opacity-30 disabled:hover:border-inherit disabled:hover:text-inherit dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
                    >
                      ↓
                    </button>
                    <button
                      type="button"
                      onClick={() => startEditMenuItem(item)}
                      className="rounded border px-2 py-1 text-sm transition-colors hover:border-amber-700 hover:text-amber-800 dark:border-stone-700 dark:text-stone-300 dark:hover:border-amber-600 dark:hover:text-amber-500"
                    >
                      編集
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDelete(item.id)}
                      className="rounded border px-2 py-1 text-sm text-red-600 dark:border-stone-700 dark:text-red-400"
                    >
                      削除
                    </button>
                  </div>
                )}
              </div>
              {pickerOpen && kaftBaseUrl && (
                <div className="mt-3 flex flex-wrap gap-2 border-t pt-3 dark:border-stone-700">
                  <button
                    type="button"
                    onClick={() => handleMenuPhotoPick(item.id, null)}
                    className="flex h-14 w-14 items-center justify-center rounded border border-dashed text-[0.6rem] text-gray-400 transition-colors hover:border-amber-500 hover:text-amber-700 dark:border-stone-700 dark:text-stone-600 dark:hover:border-amber-600 dark:hover:text-amber-500"
                  >
                    写真なし
                  </button>
                  {photos.map((photo) => (
                    <button
                      key={photo.id}
                      type="button"
                      onClick={() => handleMenuPhotoPick(item.id, photo.id)}
                      className={`h-14 w-14 shrink-0 overflow-hidden rounded border-2 transition-colors ${
                        item.photoId === photo.id
                          ? "border-amber-700 dark:border-amber-500"
                          : "border-transparent hover:border-amber-300 dark:hover:border-amber-700"
                      }`}
                    >
                      <img src={photoUrl(kaftBaseUrl, photo)} alt={photo.filename} className="h-full w-full object-cover" />
                    </button>
                  ))}
                </div>
              )}
            </li>
          );
        })}
        {menuItems.length === 0 && (
          <li className="text-gray-500 dark:text-stone-500">まだメニューがありません</li>
        )}
      </ul>

      <h2 className="mb-4 text-xl font-bold dark:text-stone-100">メニューを追加</h2>
      <form onSubmit={handleSubmit} className="form-card">
        <div className="space-y-5">
          <div className="field">
            <label className="field-label">
              <span>品名</span>
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
              placeholder="例: 自家焙煎ブレンド"
              className="field-input"
            />
          </div>

          <div className="field">
            <label className="field-label">
              <span>価格</span>
              <span className="field-tag">税込・未入力で非表示</span>
            </label>
            <div className="relative">
              <span className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-sm text-gray-400 dark:text-stone-500">
                ¥
              </span>
              <input
                type="number"
                min={0}
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                placeholder="0"
                className="field-input pl-7"
              />
            </div>
          </div>

          <div className="field">
            <label className="field-label">
              <span>説明文</span>
              <span className="field-count">{description.length}/200</span>
            </label>
            <textarea
              rows={3}
              maxLength={200}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="使用している豆や味わいの特徴など"
              className="field-textarea field-input"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="mt-6 w-full rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 disabled:opacity-50 sm:w-auto sm:px-6 dark:bg-amber-700 dark:hover:bg-amber-600"
        >
          {submitting ? "追加中..." : "追加する"}
        </button>
      </form>
    </main>
  );
}
