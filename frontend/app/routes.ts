import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),
  route("healthz", "routes/healthz.tsx"),
  route("login", "routes/login.tsx"),
  route("callback", "routes/callback.tsx"),
  route("logout", "routes/logout.tsx"),
  route("dashboard", "routes/dashboard.tsx"),
] satisfies RouteConfig;
