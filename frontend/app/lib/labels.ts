import type { BusinessCategory, OperationType, SnsPlatform } from "~/lib/api";

export const SNS_PLATFORM_LABELS: Record<SnsPlatform, string> = {
  INSTAGRAM: "Instagram",
  X: "X",
  FACEBOOK: "Facebook",
  LINE: "LINE",
  TIKTOK: "TikTok",
  YOUTUBE: "YouTube",
};

export const SNS_PLATFORMS = Object.keys(SNS_PLATFORM_LABELS) as SnsPlatform[];

export const BUSINESS_CATEGORY_LABELS: Record<BusinessCategory, string> = {
  CAFE: "カフェ",
  IZAKAYA: "居酒屋",
  RAMEN: "ラーメン店",
  RESTAURANT: "レストラン",
  KITCHEN_CAR: "キッチンカー",
  BAR: "バー",
  TEISHOKU: "定食屋",
  OTHER: "その他",
};

export const OPERATION_TYPE_LABELS: Record<OperationType, string> = {
  FIXED: "固定店舗",
  MOBILE: "移動販売",
};

export function defaultOperationType(category: BusinessCategory): OperationType {
  return category === "KITCHEN_CAR" ? "MOBILE" : "FIXED";
}
