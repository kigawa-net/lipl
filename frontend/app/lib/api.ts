import { getAccessToken } from "~/lib/oidc";

export type BusinessCategory =
  | "CAFE"
  | "IZAKAYA"
  | "RAMEN"
  | "RESTAURANT"
  | "KITCHEN_CAR"
  | "BAR"
  | "TEISHOKU"
  | "OTHER";

export type OperationType = "FIXED" | "MOBILE";

export type SnsPlatform = "INSTAGRAM" | "X" | "FACEBOOK" | "LINE" | "TIKTOK" | "YOUTUBE";

export interface SnsLinkInput {
  platform: SnsPlatform;
  url: string;
}

export interface CreateStoreRequest {
  name: string;
  businessCategory: BusinessCategory;
  operationType?: OperationType;
  address?: string;
  businessArea?: string;
  businessHours?: string;
  phone?: string;
  snsLinks: SnsLinkInput[];
}

export interface StoreResponse {
  id: number;
  slug: string;
  name: string;
  businessCategory: BusinessCategory;
  operationType: OperationType;
  address: string | null;
  businessArea: string | null;
  businessHours: string | null;
  phone: string | null;
  snsLinks: SnsLinkInput[];
}

async function authorizedFetch(path: string, init?: RequestInit): Promise<Response> {
  const token = getAccessToken();
  if (!token) {
    throw new Error("ログインが必要です");
  }
  return fetch(`/api${path}`, {
    ...init,
    headers: {
      ...init?.headers,
      Authorization: `Bearer ${token}`,
    },
  });
}

export async function listStores(): Promise<StoreResponse[]> {
  const response = await authorizedFetch("/stores");
  if (!response.ok) {
    throw new Error(`店舗一覧の取得に失敗しました（${response.status}）`);
  }
  return response.json();
}

export async function createStore(request: CreateStoreRequest): Promise<StoreResponse> {
  const response = await authorizedFetch("/stores", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null;
    throw new Error(body?.error ?? `店舗の作成に失敗しました（${response.status}）`);
  }
  return response.json();
}
