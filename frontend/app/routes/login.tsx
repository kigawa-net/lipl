import { useEffect } from "react";

// ログイン処理はバックエンド（BFF）が担う。Cookieはブラウザからは見えないため
// fetchではなく実際のページ遷移でKeycloakのログイン画面へ向かう。
export default function Login() {
  useEffect(() => {
    window.location.assign("/api/auth/login");
  }, []);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p>ログインページへ移動しています...</p>
    </main>
  );
}
