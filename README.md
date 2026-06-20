# Lipl

> お店の情報を入力するだけ。  
> 3分で公式ページを公開。

Lipl is a simple official-page builder for restaurants. It helps restaurant owners create and publish a mobile-friendly official page without HTML, CSS, servers, domains, or DNS knowledge.

## Concept

Restaurants enter basic information, menus, and photos. Lipl assists with AI questions when information is missing, generates page copy, previews the result, and publishes a public URL.

```text
店舗情報入力
↓
AI補助
↓
公式ページ生成
↓
公開
```

## MVP scope

- Restaurant profile creation
- Photo upload
- Menu registration
- AI-assisted follow-up questions
- AI-generated LP copy
- Preview
- One-click publish
- Public URL generation
- Edit and unpublish controls

## Target users

- Independent restaurants
- Cafes
- Izakaya
- Ramen shops
- Small restaurants
- Food trucks

## Plans

### Free

- 1 shop
- 1 published page
- 5 photos
- 5 menu items
- 1 AI generation
- Up to 3 AI questions
- Lipl branding shown
- No custom domain
- Claude Haiku

### Basic

- 980 JPY/month
- AI regeneration
- 20 photos
- 30 menu items
- Branding removal
- QR code generation
- News posts
- Claude Sonnet

### Pro

- 2,980 JPY/month
- Custom domain
- Contact form
- SEO settings
- Analytics
- Multilingual support
- Multiple reservation links

## Initial tech direction

- Frontend: Next.js, TypeScript, Tailwind CSS
- Backend: Supabase Auth, PostgreSQL, Storage, Edge Functions
- Hosting: Cloudflare Pages / Workers
- Object storage: Cloudflare R2
- Payments: Stripe
- AI: Anthropic Claude Haiku / Sonnet

## Product vision

Lipl aims to become a digital business card for restaurants: a single official hub for menus, opening hours, location, reservations, SNS links, and announcements.
