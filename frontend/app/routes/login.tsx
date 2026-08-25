import { redirectToLogin } from "~/lib/oidc";

export async function clientLoader() {
  await redirectToLogin();
  return null;
}

export default function Login() {
  return (
    <main className="flex min-h-screen items-center justify-center">
      <p>ログインページへ移動しています...</p>
    </main>
  );
}
