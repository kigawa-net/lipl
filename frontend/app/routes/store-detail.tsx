import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
  createMenuItem,
  deleteMenuItem,
  deletePhoto,
  getKaftBaseUrl,
  listMenuItems,
  listPhotos,
  photoUrl,
  reorderMenuItems,
  reorderPhotos,
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
        <h1 className="text-2xl font-bold">店舗管理</h1>
        <a href="/dashboard" className="text-sm text-gray-500 underline">
          店舗一覧に戻る
        </a>
      </div>

      <h2 className="mb-4 text-xl font-bold">写真</h2>
      {photoError && <p className="mb-4 text-red-600">{photoError}</p>}

      <div className="mb-4 grid grid-cols-3 gap-2">
        {photos.map((photo, index) => (
          <div key={photo.id} className="space-y-1">
            {kaftBaseUrl && (
              <img
                src={photoUrl(kaftBaseUrl, photo)}
                alt={photo.filename}
                className="aspect-square w-full rounded border object-cover"
              />
            )}
            <div className="flex items-center justify-center gap-1">
              <button
                type="button"
                onClick={() => handlePhotoMove(index, -1)}
                disabled={index === 0}
                className="rounded border px-2 py-1 text-xs disabled:opacity-30"
              >
                ↑
              </button>
              <button
                type="button"
                onClick={() => handlePhotoMove(index, 1)}
                disabled={index === photos.length - 1}
                className="rounded border px-2 py-1 text-xs disabled:opacity-30"
              >
                ↓
              </button>
              <button
                type="button"
                onClick={() => handlePhotoDelete(photo.id)}
                className="rounded border px-2 py-1 text-xs text-red-600"
              >
                削除
              </button>
            </div>
          </div>
        ))}
      </div>
      {photos.length === 0 && <p className="mb-4 text-gray-500">まだ写真がありません</p>}

      <div className="mb-8">
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={handlePhotoSelect}
          disabled={uploading || photos.length >= PHOTO_LIMIT}
          className="text-sm"
        />
        <p className="mt-1 text-xs text-gray-500">
          {uploading
            ? "アップロード中..."
            : `JPEG・PNG・WebP、10MBまで（${photos.length}/${PHOTO_LIMIT}枚）`}
        </p>
      </div>

      <h2 className="mb-4 text-xl font-bold">メニュー</h2>
      {error && <p className="mb-4 text-red-600">{error}</p>}

      <ul className="mb-8 space-y-2">
        {menuItems.map((item, index) => (
          <li key={item.id} className="flex items-center justify-between rounded border p-3">
            <div>
              <span className="font-semibold">{item.name}</span>
              {item.price != null && (
                <span className="ml-2 text-sm text-gray-500">¥{item.price.toLocaleString()}</span>
              )}
              {item.description && (
                <p className="text-sm text-gray-500">{item.description}</p>
              )}
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => handleMove(index, -1)}
                disabled={index === 0}
                className="rounded border px-2 py-1 text-sm disabled:opacity-30"
              >
                ↑
              </button>
              <button
                type="button"
                onClick={() => handleMove(index, 1)}
                disabled={index === menuItems.length - 1}
                className="rounded border px-2 py-1 text-sm disabled:opacity-30"
              >
                ↓
              </button>
              <button
                type="button"
                onClick={() => handleDelete(item.id)}
                className="rounded border px-2 py-1 text-sm text-red-600"
              >
                削除
              </button>
            </div>
          </li>
        ))}
        {menuItems.length === 0 && <li className="text-gray-500">まだメニューがありません</li>}
      </ul>

      <h2 className="mb-4 text-xl font-bold">メニューを追加</h2>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="mb-1 block text-sm font-medium">品名</label>
          <input
            required
            maxLength={50}
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded border p-2"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium">価格（税込・未入力で非表示）</label>
          <input
            type="number"
            min={0}
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            className="w-full rounded border p-2"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium">説明文</label>
          <textarea
            maxLength={200}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full rounded border p-2"
          />
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-black px-4 py-2 text-white disabled:opacity-50"
        >
          {submitting ? "追加中..." : "追加する"}
        </button>
      </form>
    </main>
  );
}
