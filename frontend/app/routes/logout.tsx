import { redirectToLogout } from "~/lib/oidc";

export async function clientLoader() {
  redirectToLogout();
  return null;
}

export default function Logout() {
  return (
    <main className="flex min-h-screen items-center justify-center">
      <p>ログアウトしています...</p>
    </main>
  );
}
