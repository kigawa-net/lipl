import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
  generateLpContent,
  getInterviewState,
  getLpContent,
  getStore,
  setStorePublished,
  updateLpContent,
  type LpContentResponse,
} from "~/lib/api";
import { isAuthenticated } from "~/lib/oidc";

export default function LpEdit() {
  const { storeId } = useParams();
  const navigate = useNavigate();
  const numericStoreId = Number(storeId);

  const [content, setContent] = useState<LpContentResponse | null>(null);
  const [hasInterview, setHasInterview] = useState(false);
  const [slug, setSlug] = useState("");
  const [published, setPublished] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [saving, setSaving] = useState(false);

  const [catchphrase, setCatchphrase] = useState("");
  const [description, setDescription] = useState("");

  useEffect(() => {
    if (!isAuthenticated()) {
      navigate("/login", { replace: true });
      return;
    }

    Promise.all([getLpContent(numericStoreId), getInterviewState(numericStoreId), getStore(numericStoreId)])
      .then(([lpContent, interviewState, store]) => {
        setContent(lpContent);
        setCatchphrase(lpContent?.catchphrase ?? "");
        setDescription(lpContent?.description ?? "");
        setHasInterview(interviewState.messages.length > 0);
        setSlug(store.slug);
        setPublished(store.published);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [numericStoreId, navigate]);

  // 生成したLPはそのまま公開する（生成→公開まで一度の操作で完結させる）。
  async function handleGenerate() {
    setGenerating(true);
    setError(null);
    try {
      const generated = await generateLpContent(numericStoreId);
      setContent(generated);
      setCatchphrase(generated.catchphrase);
      setDescription(generated.description);
      const store = await setStorePublished(numericStoreId, true);
      setPublished(store.published);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setGenerating(false);
    }
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await updateLpContent(numericStoreId, { catchphrase, description });
      setContent(updated);
      setSaved(true);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="dark:text-stone-300">読み込み中...</p>
      </main>
    );
  }

  const publicUrl =
    typeof window !== "undefined" ? `${window.location.origin}/p/${slug}` : `/p/${slug}`;

  return (
    <main className="mx-auto max-w-2xl p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold dark:text-stone-100">LP編集</h1>
        <a href={`/stores/${storeId}`} className="text-sm text-gray-500 underline dark:text-stone-500">
          店舗管理に戻る
        </a>
      </div>

      {error && <p className="mb-4 text-red-600 dark:text-red-400">{error}</p>}

      {published && (
        <div className="mb-6 flex flex-wrap items-center gap-3 rounded-lg border border-amber-200 bg-amber-50/50 p-4 dark:border-stone-700 dark:bg-stone-900">
          <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800 dark:bg-amber-900/40 dark:text-amber-400">
            公開中
          </span>
          <a
            href={`/p/${slug}`}
            target="_blank"
            rel="noreferrer"
            className="text-sm text-amber-800 underline hover:text-amber-900 dark:text-amber-500 dark:hover:text-amber-400"
          >
            {publicUrl}
          </a>
        </div>
      )}

      {content === null ? (
        <div className="form-card">
          <p className="mb-4 text-sm text-gray-600 dark:text-stone-300">
            まだLPが生成されていません。
            {hasInterview
              ? "下のボタンからAIヒアリングの内容をもとにLPを生成し、そのまま公開できます。"
              : "先にAIヒアリングでお店の魅力を教えてください。"}
          </p>
          {hasInterview ? (
            <button
              type="button"
              onClick={handleGenerate}
              disabled={generating}
              className="rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 disabled:opacity-50 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              {generating ? "生成・公開中..." : "LPを生成して公開する"}
            </button>
          ) : (
            <a
              href={`/stores/${storeId}/interview`}
              className="inline-block rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              AIヒアリングを開始する
            </a>
          )}
        </div>
      ) : (
        <form onSubmit={handleSave} className="form-card">
          <div className="form-section">
            <div className="space-y-5">
              <div className="field">
                <label className="field-label">
                  <span>キャッチコピー</span>
                  <span className="field-count">{catchphrase.length}/200</span>
                </label>
                <input
                  required
                  maxLength={200}
                  value={catchphrase}
                  onChange={(e) => setCatchphrase(e.target.value)}
                  className="field-input"
                />
              </div>

              <div className="field">
                <label className="field-label">
                  <span>紹介文</span>
                  <span className="field-count">{description.length}/2000</span>
                </label>
                <textarea
                  required
                  rows={6}
                  maxLength={2000}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="field-textarea field-input"
                />
              </div>
            </div>
          </div>

          {saved && !error && <p className="mt-4 text-sm text-amber-800 dark:text-amber-500">保存しました</p>}

          <div className="mt-6 flex flex-wrap gap-3">
            <button
              type="submit"
              disabled={saving}
              className="rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 disabled:opacity-50 dark:bg-amber-700 dark:hover:bg-amber-600"
            >
              {saving ? "保存中..." : "保存する"}
            </button>
            <button
              type="button"
              onClick={handleGenerate}
              disabled={generating}
              className="rounded-lg border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-600 transition-colors hover:border-gray-300 disabled:opacity-50 dark:border-stone-700 dark:text-stone-300 dark:hover:border-stone-600"
            >
              {generating ? "再生成・公開中..." : "AIで再生成して公開する"}
            </button>
          </div>
        </form>
      )}
    </main>
  );
}
