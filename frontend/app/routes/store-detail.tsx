import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
  createMenuItem,
  deleteMenuItem,
  deleteMenuItemPhoto,
  deletePhoto,
  getKaftBaseUrl,
  listMenuItems,
  listPhotos,
  photoUrl,
  reorderMenuItems,
  reorderPhotos,
  uploadMenuItemPhoto,
  uploadPhoto,
  type MenuItemResponse,
  type PhotoResponse,
} from "~/lib/api";
import { isAuthenticated } from "~/lib/oidc";

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

  const [photos, setPhotos] = useState<PhotoResponse[]>([]);
  const [kaftBaseUrl, setKaftBaseUrl] = useState<string | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [menuPhotoUploadingId, setMenuPhotoUploadingId] = useState<number | null>(null);

  const numericStoreId = Number(storeId);

  useEffect(() => {
    if (!isAuthenticated()) {
      navigate("/login", { replace: true });
      return;
    }
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

  async function handleMenuPhotoSelect(menuItemId: number, e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    setError(null);

    if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
      setError("JPEG・PNG・WebP形式の画像のみアップロードできます");
      return;
    }
    if (file.size > MAX_PHOTO_SIZE) {
      setError("画像サイズは10MB以下にしてください");
      return;
    }

    setMenuPhotoUploadingId(menuItemId);
    try {
      const updated = await uploadMenuItemPhoto(numericStoreId, menuItemId, file);
      setMenuItems((prev) => prev.map((item) => (item.id === menuItemId ? updated : item)));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setMenuPhotoUploadingId(null);
    }
  }

  async function handleMenuPhotoRemove(menuItemId: number) {
    setError(null);
    try {
      await deleteMenuItemPhoto(numericStoreId, menuItemId);
      setMenuItems((prev) =>
        prev.map((item) =>
          item.id === menuItemId ? { ...item, photoKaftUuid: null, photoFilename: null } : item,
        ),
      );
    } catch (e) {
      setError((e as Error).message);
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
        <h1 className="text-2xl font-bold dark:text-stone-100">店舗管理</h1>
        <a href="/dashboard" className="text-sm text-gray-500 underline dark:text-stone-500">
          店舗一覧に戻る
        </a>
      </div>

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
        {menuItems.map((item, index) => (
          <li
            key={item.id}
            className="flex items-center justify-between gap-3 rounded border p-3 dark:border-stone-700"
          >
            <div className="flex min-w-0 items-center gap-3">
              {item.photoKaftUuid && kaftBaseUrl ? (
                <img
                  src={photoUrl(kaftBaseUrl, { kaftUuid: item.photoKaftUuid, filename: item.photoFilename ?? "" })}
                  alt={item.name}
                  className="h-14 w-14 shrink-0 rounded object-cover dark:border dark:border-stone-700"
                />
              ) : (
                <label className="flex h-14 w-14 shrink-0 cursor-pointer items-center justify-center rounded border border-dashed text-[0.65rem] text-gray-400 transition-colors hover:border-amber-500 hover:text-amber-700 dark:border-stone-700 dark:text-stone-600 dark:hover:border-amber-600 dark:hover:text-amber-500">
                  {menuPhotoUploadingId === item.id ? "..." : "写真"}
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    onChange={(e) => handleMenuPhotoSelect(item.id, e)}
                    disabled={menuPhotoUploadingId === item.id}
                    className="hidden"
                  />
                </label>
              )}
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
                {item.photoKaftUuid && (
                  <button
                    type="button"
                    onClick={() => handleMenuPhotoRemove(item.id)}
                    className="text-xs text-red-600 underline hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
                  >
                    写真を削除
                  </button>
                )}
              </div>
            </div>
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
                onClick={() => handleDelete(item.id)}
                className="rounded border px-2 py-1 text-sm text-red-600 dark:border-stone-700 dark:text-red-400"
              >
                削除
              </button>
            </div>
          </li>
        ))}
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
