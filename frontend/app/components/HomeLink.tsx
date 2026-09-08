// 管理画面ヘッダーに共通で置く、ホーム（/）へ戻るための目立つロゴボタン。
// 単なるテキストリンクだと視認性が低く押しにくいため、アイコン付きのバッジ状ボタンにする。
export function HomeLink() {
  return (
    <a
      href="/"
      className="inline-flex items-center gap-1.5 rounded-lg bg-amber-900 px-3 py-1.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-amber-800 dark:bg-amber-600 dark:hover:bg-amber-500"
    >
      <svg
        viewBox="0 0 20 20"
        fill="currentColor"
        aria-hidden="true"
        className="h-4 w-4 shrink-0"
      >
        <path d="M10 2.2 1.5 9.4a.75.75 0 0 0 .97 1.14l.78-.66V17a1 1 0 0 0 1 1H8a1 1 0 0 0 1-1v-4h2v4a1 1 0 0 0 1 1h3.75a1 1 0 0 0 1-1v-7.12l.78.66a.75.75 0 0 0 .97-1.14L10 2.2Z" />
      </svg>
      Lipl
    </a>
  );
}
