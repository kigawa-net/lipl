import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { handleCallback } from "~/lib/oidc";

export default function Callback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const code = searchParams.get("code");
    const errorParam = searchParams.get("error");

    if (errorParam) {
      setError(`Keycloakでエラーが発生しました: ${errorParam}`);
      return;
    }
    if (!code) {
      setError("認可コードが見つかりません");
      return;
    }

    handleCallback(code)
      .then(() => navigate("/dashboard", { replace: true }))
      .catch((e: Error) => setError(e.message));
  }, [searchParams, navigate]);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p>{error ?? "ログイン処理中..."}</p>
    </main>
  );
}
