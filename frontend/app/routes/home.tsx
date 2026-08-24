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
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-8 text-center">
      <h1 className="text-3xl font-bold">Lipl</h1>
      <p className="text-lg text-gray-600">
        お店の情報を入力するだけ。
        <br />
        3分で公式ページを公開。
      </p>
    </main>
  );
}
