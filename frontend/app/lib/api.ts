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

export interface CreateMenuItemRequest {
  name: string;
  price?: number;
  description?: string;
}

export interface MenuItemResponse {
  id: number;
  storeId: number;
  name: string;
  price: number | null;
  description: string | null;
  displayOrder: number;
}

async function menuItemErrorMessage(response: Response, fallback: string): Promise<string> {
  const body = (await response.json().catch(() => null)) as { error?: string } | null;
  return body?.error ?? `${fallback}（${response.status}）`;
}

export async function listMenuItems(storeId: number): Promise<MenuItemResponse[]> {
  const response = await authorizedFetch(`/stores/${storeId}/menu-items`);
  if (!response.ok) {
    throw new Error(await menuItemErrorMessage(response, "メニュー一覧の取得に失敗しました"));
  }
  return response.json();
}

export async function createMenuItem(
  storeId: number,
  request: CreateMenuItemRequest,
): Promise<MenuItemResponse> {
  const response = await authorizedFetch(`/stores/${storeId}/menu-items`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!response.ok) {
    throw new Error(await menuItemErrorMessage(response, "メニューの作成に失敗しました"));
  }
  return response.json();
}

export async function deleteMenuItem(storeId: number, menuItemId: number): Promise<void> {
  const response = await authorizedFetch(`/stores/${storeId}/menu-items/${menuItemId}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error(await menuItemErrorMessage(response, "メニューの削除に失敗しました"));
  }
}

export async function reorderMenuItems(storeId: number, orderedIds: number[]): Promise<void> {
  const response = await authorizedFetch(`/stores/${storeId}/menu-items/reorder`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ orderedIds }),
  });
  if (!response.ok) {
    throw new Error(await menuItemErrorMessage(response, "メニューの並び替えに失敗しました"));
  }
}

export interface PhotoResponse {
  id: number;
  storeId: number;
  kaftUuid: string;
  filename: string;
  displayOrder: number;
}

interface UploadTokenResponse {
  uuid: string;
  uploadToken: string;
  kaftBaseUrl: string;
}

async function photoErrorMessage(response: Response, fallback: string): Promise<string> {
  const body = (await response.json().catch(() => null)) as { error?: string } | null;
  return body?.error ?? `${fallback}（${response.status}）`;
}

export function photoUrl(kaftBaseUrl: string, photo: PhotoResponse): string {
  return `${kaftBaseUrl}/files/${photo.kaftUuid}/${encodeURIComponent(photo.filename)}`;
}

let cachedKaftBaseUrl: string | null = null;

export async function getKaftBaseUrl(): Promise<string> {
  if (cachedKaftBaseUrl) {
    return cachedKaftBaseUrl;
  }
  const response = await fetch("/api/kaft-config");
  if (!response.ok) {
    throw new Error(`kaft設定の取得に失敗しました（${response.status}）`);
  }
  const { kaftBaseUrl }: { kaftBaseUrl: string } = await response.json();
  cachedKaftBaseUrl = kaftBaseUrl;
  return kaftBaseUrl;
}

export async function listPhotos(storeId: number): Promise<PhotoResponse[]> {
  const response = await authorizedFetch(`/stores/${storeId}/photos`);
  if (!response.ok) {
    throw new Error(await photoErrorMessage(response, "写真一覧の取得に失敗しました"));
  }
  return response.json();
}

// アップロード本体（ファイルのバイト列）はブラウザからkaftへ直接PUTされ、
// lipl backendは経由しない（アーキテクチャ上の制約。PhotoModels.kt参照）。
export async function uploadPhoto(storeId: number, file: File): Promise<PhotoResponse> {
  const tokenResponse = await authorizedFetch(`/stores/${storeId}/photos/upload-token`, {
    method: "POST",
  });
  if (!tokenResponse.ok) {
    throw new Error(await photoErrorMessage(tokenResponse, "アップロード準備に失敗しました"));
  }
  const { uuid, uploadToken, kaftBaseUrl }: UploadTokenResponse = await tokenResponse.json();

  const putResponse = await fetch(`${kaftBaseUrl}/files/${uuid}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${uploadToken}`,
      "Content-Type": file.type || "application/octet-stream",
    },
    body: file,
  });
  if (!putResponse.ok) {
    throw new Error(`画像のアップロードに失敗しました（${putResponse.status}）`);
  }

  const confirmResponse = await authorizedFetch(`/stores/${storeId}/photos/confirm`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ uuid, filename: file.name }),
  });
  if (!confirmResponse.ok) {
    throw new Error(await photoErrorMessage(confirmResponse, "写真の登録に失敗しました"));
  }
  return confirmResponse.json();
}

export async function deletePhoto(storeId: number, photoId: number): Promise<void> {
  const response = await authorizedFetch(`/stores/${storeId}/photos/${photoId}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error(await photoErrorMessage(response, "写真の削除に失敗しました"));
  }
}

export async function reorderPhotos(storeId: number, orderedIds: number[]): Promise<void> {
  const response = await authorizedFetch(`/stores/${storeId}/photos/reorder`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ orderedIds }),
  });
  if (!response.ok) {
    throw new Error(await photoErrorMessage(response, "写真の並び替えに失敗しました"));
  }
}
