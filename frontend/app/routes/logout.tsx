import { useEffect } from "react";

export default function Logout() {
  useEffect(() => {
    window.location.assign("/api/auth/logout");
  }, []);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p>ログアウトしています...</p>
    </main>
  );
}
