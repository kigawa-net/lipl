import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),
  route("healthz", "routes/healthz.tsx"),
  route("login", "routes/login.tsx"),
  route("callback", "routes/callback.tsx"),
  route("logout", "routes/logout.tsx"),
  route("dashboard", "routes/dashboard.tsx"),
  route("stores/new", "routes/store-wizard.tsx"),
  route("stores/:storeId", "routes/store-detail.tsx"),
  route("stores/:storeId/interview", "routes/interview.tsx"),
  route("stores/:storeId/lp", "routes/lp-edit.tsx"),
  route("p/:slug", "routes/public-store.tsx"),
] satisfies RouteConfig;
