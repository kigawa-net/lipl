import type { Route } from "./+types/home";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "Lipl" },
    {
      name: "description",
      content: "お店の情報を入力するだけ。3分で公式ページを公開。",
    },
  ];
}

export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 bg-gradient-to-b from-amber-50 to-white p-8 text-center dark:from-stone-900 dark:to-stone-950">
      <h1 className="text-3xl font-bold text-amber-900 dark:text-amber-500">Lipl</h1>
      <p className="text-lg text-gray-600 dark:text-stone-400">
        お店の情報を入力するだけ。
        <br />
        3分で公式ページを公開。
      </p>
      <a
        href="/dashboard"
        className="rounded bg-amber-900 px-6 py-3 text-white transition-colors hover:bg-amber-800 dark:bg-amber-700 dark:hover:bg-amber-600"
      >
        はじめる
      </a>
    </main>
  );
}
