import js from "@eslint/js";
import tseslint from "typescript-eslint";

export default tseslint.config(
  { ignores: ["build/", ".react-router/", "node_modules/"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    rules: {
      // CLAUDE.md: any 禁止、型を必ず定義する
      "@typescript-eslint/no-explicit-any": "error",
      // React Router の `meta({}: Route.MetaArgs)` 等、フレームワーク側の型に
      // 合わせた未使用引数の空分割代入を許容する
      "no-empty-pattern": "off",
    },
  },
);
