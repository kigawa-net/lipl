import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [
  index("routes/dashboard.tsx"),
  route("healthz", "routes/healthz.tsx"),
  route("login", "routes/login.tsx"),
  route("logout", "routes/logout.tsx"),
  route("lp", "routes/home.tsx"),
  route("stores/new", "routes/store-wizard.tsx"),
  route("stores/:storeId", "routes/store-detail.tsx"),
  route("stores/:storeId/interview", "routes/interview.tsx"),
  route("p/:slug", "routes/public-store.tsx"),
] satisfies RouteConfig;
