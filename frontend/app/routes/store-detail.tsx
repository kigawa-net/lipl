import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
  createMenuItem,
  deleteMenuItem,
  listMenuItems,
  reorderMenuItems,
  type MenuItemResponse,
} from "~/lib/api";
import { isAuthenticated } from "~/lib/oidc";

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
  }, [numericStoreId, navigate]);

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
        <h1 className="text-2xl font-bold">メニュー管理</h1>
        <a href="/dashboard" className="text-sm text-gray-500 underline">
          店舗一覧に戻る
        </a>
      </div>

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
