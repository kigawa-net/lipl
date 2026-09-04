import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { getInterviewState, sendInterviewMessage, type InterviewStateResponse } from "~/lib/api";
import { isAuthenticated } from "~/lib/oidc";

export default function Interview() {
  const { storeId } = useParams();
  const navigate = useNavigate();
  const numericStoreId = Number(storeId);

  const [state, setState] = useState<InterviewStateResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [answer, setAnswer] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) {
      navigate("/login", { replace: true });
      return;
    }

    getInterviewState(numericStoreId)
      .then(async (initial) => {
        if (initial.messages.length === 0) {
          const started = await sendInterviewMessage(numericStoreId);
          setState(started);
        } else {
          setState(initial);
        }
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [numericStoreId, navigate]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!answer.trim()) return;
    setSending(true);
    setError(null);
    try {
      const updated = await sendInterviewMessage(numericStoreId, answer);
      setState(updated);
      setAnswer("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSending(false);
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
        <h1 className="text-2xl font-bold dark:text-stone-100">AIヒアリング</h1>
        <a href={`/stores/${storeId}`} className="text-sm text-gray-500 underline dark:text-stone-500">
          店舗管理に戻る
        </a>
      </div>

      <p className="mb-4 text-sm text-gray-500 dark:text-stone-400">
        AIがいくつか質問しますので、お店の魅力やこだわりを教えてください（質問は最大{state?.questionLimit ?? 3}回）。
      </p>

      {error && <p className="mb-4 text-red-600 dark:text-red-400">{error}</p>}

      <div className="mb-6 space-y-3">
        {state?.messages.map((message) => (
          <div
            key={message.id}
            className={message.role === "assistant" ? "flex justify-start" : "flex justify-end"}
          >
            <div
              className={
                message.role === "assistant"
                  ? "max-w-[80%] rounded-lg rounded-bl-none bg-amber-50 px-4 py-2 text-sm text-gray-800 dark:bg-stone-800 dark:text-stone-100"
                  : "max-w-[80%] rounded-lg rounded-br-none bg-amber-900 px-4 py-2 text-sm text-white dark:bg-amber-700"
              }
            >
              {message.content}
            </div>
          </div>
        ))}
      </div>

      {state?.limitReached ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm dark:border-stone-700 dark:bg-stone-900">
          <p className="mb-3 text-gray-700 dark:text-stone-300">
            質問回数の上限に達しました。この内容をもとにLPを生成しましょう。
          </p>
          <a
            href={`/stores/${storeId}/lp`}
            className="inline-block rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 dark:bg-amber-700 dark:hover:bg-amber-600"
          >
            LPを生成する
          </a>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="flex gap-2">
          <input
            required
            maxLength={500}
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            placeholder="回答を入力"
            disabled={sending}
            className="field-input flex-1"
            autoFocus
          />
          <button
            type="submit"
            disabled={sending}
            className="rounded-lg bg-amber-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-amber-800 disabled:opacity-50 dark:bg-amber-700 dark:hover:bg-amber-600"
          >
            {sending ? "送信中..." : "送信"}
          </button>
        </form>
      )}
    </main>
  );
}
