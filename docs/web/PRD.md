# PRD — Web App and Backend (multi-tenant)

## 1. Summary

A multi-tenant delivery-tracking platform for small Kenyan shops. Any shop can sign up, add its riders and send customers live tracking links. The first tenant is a Nakuru clothes shop doing about 10 deliveries a day; the platform owner adds it directly.

One website plus one Supabase backend (shared with the Android rider app):

- **Platform pages:** marketing landing `/`, sign up `/signup`, sign in `/login`.
- **Owner dashboard** `/app`: create deliveries, send tracking links, assign riders, watch riders live, record payments, manage riders, customers and shop settings.
- **Shop page** `/{shop}`: a small public page per shop.
- **Customer tracking page** `/{shop}/t/{token}`: confirm the delivery spot, then watch the rider approach with a live map and ETA. No app, no login.
- **Platform admin** `/platform`: the platform owner approves, suspends and creates shops.

The platform is called **Fika** (Swahili for "arrive") at **fika.co.ke**; the rider app is **Fika Rider**. Messaging is free by default (WhatsApp links sent by the owner); automatic SMS is a later paid extra.

## 2. Users

- **Shop owner** — runs one shop (occasionally more), from a phone or a PC. Non-technical.
- **Rider** — uses the Android app; belongs to one shop per account.
- **Customer** — opens a tracking link on their phone, often on mobile data.
- **Platform admin** — the platform owner. Approves shops, supports them, watches usage.

## 3. Goals

1. A shop can sign up and send its first tracking link within 15 minutes.
2. The owner can create a delivery and send the link in under a minute.
3. A new customer at home confirms their location in two taps; a returning customer never has to again.
4. The tracking page shows first content in under 3 s on 3G-class networks.
5. **No shop can ever see another shop's data**, and no customer can see another customer's order.

## 4. Non-goals (v1)

- Subscription billing inside the app (invoice shops manually for now).
- Custom domains or subdomains per shop.
- Staff roles beyond owner (an owner can invite a co-owner; finer roles later).
- Selling products, carts, online payments, automatic SMS, route optimisation.
- Customer accounts or order history.

## 5. Tenancy model

- **Tenant = shop.** Every business row carries `tenant_id`. RLS policies check membership on every table; there is no query path that skips it.
- **Memberships** link users to tenants with a role: `owner` or `rider`. A user can be an owner of several shops (shop switcher in the dashboard).
- **Riders get one auth account per shop.** Their login email is synthetic and includes the shop's immutable code: `254712345678@k7q2mx.riders.invalid`. In the rider app they type **shop code + phone + PIN**. This keeps rider identity simple and avoids SMS costs.
- **Slug vs code.** `slug` (e.g. `amani-boutique`) is for URLs and can be changed by the owner. `shop_code` (6 characters, e.g. `K7Q2MX`) never changes and is used for rider sign-in.
- **Tracking tokens are globally unique**, so a link resolves by token alone. The slug in the URL is for trust and branding; if it doesn't match the token's shop (or the shop renamed its slug), the page redirects to the current URL.
- **Reserved slugs:** `app`, `login`, `signup`, `platform`, `api`, `t`, `admin`, `help`, `about`, `pricing`, `terms`, `privacy`, plus anything shorter than 3 characters.
- **Shop status:** `pending` (signed up, awaiting approval), `active`, `suspended`. While pending, the owner can set up the shop and add riders but cannot send tracking links. Suspended shops keep their data; tracking pages show a neutral "not available" state.
- **Usage cap per shop** (default 600 deliveries/month) protects the shared free tiers of Google Navigation, Mapbox and Supabase. The dashboard shows usage; the platform admin can raise the cap.

## 6. Order lifecycle

```
created ─▶ assigned ─▶ picked_up ─▶ on_the_way ─▶ delivered
                                         └────────▶ returned
   └──────────────(owner cancels any time before delivered)──────────▶ cancelled
```

- **Returned** (rider, from `on_the_way` only): the customer couldn't pay or refused the items, and the rider is bringing them back. Terminal for the rider. The owner confirms on the dashboard that the items are back (`return_received_at`). To try again, the owner creates a new order (one tap, *Send again*, prefilled from the returned one).
- If the customer can't pay but keeps the items, the order is `delivered` with `payment_status = unpaid` and an `unpaid_reason` instead. The rider asks the owner which one applies.

Separate from status:
- **Location**: `location_confirmed_at` set when the customer, owner or rider provides a pin.
- **Payment**: `payment_mode` (`prepaid` | `pay_on_delivery`), `payment_status` (`unpaid` | `paid`), and for pay-on-delivery orders `payment_method` (`cash` | `mpesa`) with an optional `mpesa_code` the rider records.
- **Link sent**: `link_sent_at` set when the owner taps *Send on WhatsApp*.

## 7. Features

### 7.1 Platform landing `/`
- What it is ("Live delivery tracking for your shop"), how it works in three steps, sign up and sign in buttons. Prerendered.

### 7.2 Sign up `/signup`
Two short steps on one page:
1. **You:** full name, email, phone, password. Email is verified with a link before the dashboard opens.
2. **Your shop:** shop name, web address (slug auto-suggested from the name, editable, live availability check against existing and reserved slugs), town, shop phone/WhatsApp.

On submit: create the auth user, then the `create_tenant` RPC creates the tenant (`pending`, with a generated `shop_code`) and an `owner` membership. The owner lands on the dashboard with the setup checklist and a "We're reviewing your shop" banner.

### 7.3 Sign in `/login`
Email + password, "Forgot password?" (Supabase reset email), link to sign up. After sign-in: one shop → its dashboard; several → shop picker. Rider accounts are refused here with "Riders use the Fika Rider app".

### 7.4 First-run setup checklist (dashboard)
Shown on Today until done or dismissed:
1. Add your M-Pesa till or paybill number.
2. Add your shop phone and WhatsApp number (prefilled from sign-up).
3. Add your first rider.
4. Upload a logo (optional).
5. Send your first delivery (enabled once the shop is approved).

### 7.5 Shop settings `/app/settings`
- Shop name, web address (slug; warns that old links keep working), town, phone, WhatsApp.
- Payment: M-Pesa till or paybill (+ account number), shown on tracking pages and to riders.
- Logo (Supabase Storage, square, max 1 MB).
- WhatsApp message template with placeholders `{name}`, `{items}`, `{link}`, `{amount}`, `{till}`.
- Shop code (read-only, with copy) for rider sign-in.
- Team: co-owners (invite by email).
- Usage this month vs cap.

### 7.6 Shop page `/{shop}`
The per-shop public page (formerly the single landing page): logo or name, short line, WhatsApp and call buttons, "Tracking an order? Open the link we sent you", how delivery works, till number. Small "Powered by Fika" footer.

### 7.7 Tracking page `/{shop}/t/{token}`

Branded with the shop's name and logo. States, driven by the `track` response:

| State | Shows |
| --- | --- |
| Invalid or unknown token | "We couldn't find this delivery." Shop contact button if the shop is known |
| Shop suspended | "This delivery link isn't available. Contact the shop." |
| Needs location (`location_confirmed_at` null, before `on_the_way`) | Location step (below) |
| Confirmed, waiting | Progress tracker, "We're getting your order ready", saved spot with *Change* |
| Assigned / picked up | Rider first name, "will set off soon" |
| On the way | Live map with rider and destination, ETA, distance, call rider, last-updated time |
| Any state, pay on delivery and unpaid | Payment card: amount and the shop's till/paybill, cash also fine |
| Delivered | Success state and receipt, no map, no rider location. After 24 h: completed state with no personal data |
| Cancelled | "This delivery was cancelled. Contact the shop." |
| Returned | "This delivery couldn't be completed. Contact the shop." No map, no rider location |

**Location step:** **Use my current location** → map with the pin → **Deliver here**; or choose on the map with search (Mapbox geocoding biased to the shop's town); optional landmark note (140 chars); allowed until `on_the_way`. Saved location is offered first for returning customers.

Other: polls `track` every 10 s while visible; map loads only when needed; rider's phone only while `on_the_way`, shop phone otherwise; `noindex`.

### 7.8 Owner dashboard `/app`
Same as the single-shop design, scoped to the selected shop:
- **Today:** needs attention (no location, link not sent, returned with items still with the rider), in-progress table with inline rider assignment, delivered today, live riders map, counts.
- **New order:** phone lookup prefills returning customers, items, payment mode and amount, location (saved / customer pins / paste link / pick on map), rider, delivery notes; **Save and send on WhatsApp** using the shop's template.
- **Order detail:** status, actions (copy link, resend, cancel, mark paid, reassign until `on_the_way`; for returned orders: *Items received* and *Send again*), map, cards, timeline from `order_events`. Returned orders show the rider's `return_reason`.
- **Payments:** cash with riders (mark received), M-Pesa to check against the till statement, delivered but unpaid, returned (items not yet received).
- **Riders:** list, status, today's count, cash held; add rider (PIN shown once), reset PIN, deactivate/reactivate. Deactivating also bans the rider's auth user, so the app shows "Your rider account is turned off" instead of a wrong-PIN error and existing sessions stop refreshing. The add-rider panel shows the shop code to give the rider.
- **Customers:** searchable list, saved location (edit/clear), recent orders, new order for this customer.
- **Shop switcher** in the menu when the user owns more than one shop.

### 7.9 Platform admin `/platform`
Only users in `platform_admins`:
- Shops list: name, slug, town, status, created, deliveries this month vs cap.
- Approve, suspend, reactivate, change cap.
- **Create shop for someone** (used for the first client): creates the tenant as `active` and sends the owner an invite email to set their password.

### 7.10 Backend jobs
- **ETA fallback:** `pg_cron` every minute calls `eta-fallback` for `on_the_way` orders with stale ETAs (Mapbox Directions `driving-traffic`).
- **Cleanup:** daily job removes `rider_positions` for riders not on a delivery; nulls stale ETAs; purges personal data from tracking responses after 24 h (handled in `track`).
- **Usage counter:** increments per tenant per month on order creation; blocks new orders at the cap with a clear message.
- **Backups:** nightly `pg_dump` via GitHub Actions to Cloudflare R2, 14 days kept.

## 8. Data model

Every business table has `tenant_id uuid not null references tenants`. Child rows reference parents with composite foreign keys `(tenant_id, id)` so a row can never point at another shop's customer, rider or order.

```sql
create type member_role    as enum ('owner', 'rider');
create type tenant_status  as enum ('pending', 'active', 'suspended');
create type order_status   as enum ('created','assigned','picked_up','on_the_way','delivered','returned','cancelled');
create type payment_mode   as enum ('prepaid','pay_on_delivery');
create type payment_status as enum ('unpaid','paid');
create type payment_method as enum ('cash','mpesa');

tenants (
  id uuid pk default gen_random_uuid(),
  slug citext unique not null,          -- URL, changeable, not reserved
  shop_code text unique not null,       -- 6 chars A-Z0-9, immutable, rider sign-in
  name text not null,
  town text not null,
  phone text,                           -- E.164
  whatsapp text,                        -- E.164
  till_number text,
  paybill_number text,
  paybill_account text,
  logo_path text,                       -- Supabase Storage
  message_template text,
  status tenant_status not null default 'pending',
  monthly_order_cap integer not null default 600,
  created_by uuid references auth.users,
  created_at timestamptz default now()
)

tenant_slug_history (                   -- old slugs keep redirecting
  slug citext pk, tenant_id uuid not null references tenants, retired_at timestamptz default now()
)

profiles (                              -- one per auth user, global
  id uuid pk references auth.users,
  full_name text not null,
  phone text,                           -- E.164
  created_at timestamptz default now()
)

memberships (
  tenant_id uuid references tenants,
  user_id uuid references auth.users,
  role member_role not null,
  active boolean not null default true,
  created_at timestamptz default now(),
  primary key (tenant_id, user_id)
)

platform_admins ( user_id uuid pk references auth.users )

usage_monthly ( tenant_id uuid, month date, orders integer default 0, primary key (tenant_id, month) )

customers (
  tenant_id uuid not null references tenants,
  id uuid default gen_random_uuid(),
  phone text not null,                  -- E.164
  name text,
  saved_lat double precision, saved_lng double precision, landmark text,
  created_at timestamptz default now(),
  primary key (tenant_id, id),
  unique (tenant_id, phone)
)

orders (
  tenant_id uuid not null references tenants,
  id uuid default gen_random_uuid(),
  code text not null,                   -- short human code, unique per shop
  tracking_token text unique not null,  -- globally unique, 22-char base64url
  customer_id uuid not null,
  rider_id uuid,
  status order_status not null default 'created',
  items text not null,
  rider_notes text,
  payment_mode payment_mode not null,
  payment_status payment_status not null default 'unpaid',
  payment_method payment_method,
  mpesa_code text,
  mpesa_verified_at timestamptz,
  cash_received_at timestamptz,         -- owner received the cash from the rider
  unpaid_reason text,                   -- delivered but customer didn't pay
  return_reason text,                   -- rider brought the items back
  return_received_at timestamptz,       -- owner confirmed the items are back
  amount_kes integer,
  dest_lat double precision, dest_lng double precision, landmark text,
  location_confirmed_at timestamptz,
  link_sent_at timestamptz,
  eta_seconds integer, distance_m integer, eta_updated_at timestamptz,
  created_at timestamptz default now(),
  assigned_at timestamptz, picked_up_at timestamptz, on_the_way_at timestamptz,
  delivered_at timestamptz, returned_at timestamptz, cancelled_at timestamptz,
  primary key (tenant_id, id),
  unique (tenant_id, code),
  foreign key (tenant_id, customer_id) references customers (tenant_id, id),
  foreign key (tenant_id, rider_id) references memberships (tenant_id, user_id)
)

rider_positions (                       -- latest only, one row per rider per shop
  tenant_id uuid not null,
  rider_id uuid not null,
  lat double precision not null, lng double precision not null,
  heading real, speed real, accuracy real,
  updated_at timestamptz not null default now(),
  primary key (tenant_id, rider_id),
  foreign key (tenant_id, rider_id) references memberships (tenant_id, user_id)
)

order_events (                          -- append-only audit trail
  id bigint generated always as identity primary key,
  tenant_id uuid not null,
  order_id uuid not null,
  actor_id uuid,                        -- null for customer or system
  actor_kind text not null,             -- 'owner' | 'rider' | 'customer' | 'system' | 'platform'
  event text not null,
  data jsonb,
  created_at timestamptz not null default now(),
  foreign key (tenant_id, order_id) references orders (tenant_id, id) on delete cascade
)
```

**RLS helpers** (`security definer`, `stable`, `search_path` pinned):
- `is_member(t uuid, roles member_role[])` → caller has an active membership in `t` with one of `roles`.
- `is_platform_admin()`.

**Policies (pattern):**
- Owners: `is_member(tenant_id, '{owner}')` for select/insert/update on customers, orders, order_events (insert only), rider_positions (select), memberships of their tenant, their tenant row (update limited columns via RPC).
- Riders (select only; all writes through `rider_*` RPCs):
    - `orders` where `rider_id = auth.uid() and is_member(tenant_id, '{rider}')`.
    - `customers` linked to one of their open orders: `exists (select 1 from orders o where o.tenant_id = customers.tenant_id and o.customer_id = customers.id and o.rider_id = auth.uid() and o.status in ('assigned','picked_up','on_the_way')) and is_member(tenant_id, '{rider}')`. Past orders go through `rider_history`, which hides the phone after 24 h.
    - `tenants` where `is_member(id, '{rider}')`, regardless of `status`, so the app can show the shop's name, logo, till and a paused state. Riders see the whole row; it holds nothing secret (template, cap). Column-level grants can't distinguish riders from owners, since both use the `authenticated` role.
    - `profiles` where `id = auth.uid()` (Account screen).
- Realtime: `orders` is in the `supabase_realtime` publication; RLS applies, so a rider only receives changes to their own orders. A reassigned order stops matching the old rider's policy and sends them nothing; the app detects it by refetching.
- `anon`: no table access at all.
- Platform admin: reads all via `/platform` RPCs, never through the owner policies.

**Rider history view:** as before, filtered by `rider_id = auth.uid()` and the rider's active membership; covers `delivered`, `returned` and `cancelled` orders; exposes first name, landmark, payment outcome (including returned), and the customer phone only for 24 h.

Indexes: `orders(tenant_id, status)`, `orders(tenant_id, rider_id, status)`, `orders(tracking_token)`, `orders(tenant_id, customer_id)`, `order_events(tenant_id, order_id, created_at)`, `memberships(user_id)`.

Constraints: at most one `on_the_way` order per rider per shop (partial unique index on `(tenant_id, rider_id)` where `status = 'on_the_way'`). Confirmed order locations are copied to the customer's saved location.

## 9. API surface

### RPCs
| RPC | Caller | Effect |
| --- | --- | --- |
| `create_tenant(name, slug, town, phone)` | signed-in user | Validates slug, creates `pending` tenant with `shop_code`, adds caller as owner |
| `update_tenant_settings(tenant_id, …)` | owner | Changes allowed settings; slug change writes `tenant_slug_history` |
| `check_slug(slug)` | anyone | Available / taken / reserved (rate-limited) |
| `rider_shop_lookup(shop_code)` | anyone (rate-limited per IP) | Returns `{ name, logo_url, status }` for the shop, or null. Used by the rider app before sign-in to tell an unknown code from a wrong PIN and to show a paused shop. Exposes nothing beyond the public shop page |
| `rider_mark_picked_up(order_id)` | rider | `assigned` → `picked_up` |
| `rider_start_delivery(order_id)` | rider | → `on_the_way`; one at a time |
| `rider_report_position(lat, lng, heading, speed, accuracy, eta_seconds, distance_m)` | rider | Upsert position for the rider's active shop; update the active order's ETA. Returns `{ order_id, status }` of the rider's latest non-final order or null, so the app stops tracking after a cancel even if Realtime missed it |
| `rider_mark_delivered(order_id, payment_method, mpesa_code, unpaid_reason, save_lat, save_lng)` | rider | → `delivered`, payment outcome, optional saved location |
| `rider_mark_returned(order_id, return_reason)` | rider | `on_the_way` → `returned`; sets `returned_at`, `return_reason`; deletes the rider's `rider_positions` row; writes an `order_events` row |

All rider RPCs derive `tenant_id` from the rider's single active membership; they never take it as a parameter. They raise errors with stable codes the app maps to copy (in the exception's `hint` or as the message): `shop_suspended` (tenant status is `suspended`; `pending` shops still let riders sign in and see no orders), `rider_inactive` (membership inactive), `not_your_order`, `already_on_the_way`, `invalid_status`.

### Edge functions
| Function | Auth | Purpose |
| --- | --- | --- |
| `track` (GET `?token=&shop=`) | none | Whitelisted view of one order plus shop branding; `redirect_slug` when the slug is stale |
| `track-set-location` (POST) | none, token | Sets destination + landmark before `on_the_way` |
| `admin-rider` (POST) | owner JWT + `tenant_id` | `create`, `reset_pin`, `set_active`; checks `is_member(tenant_id, '{owner}')`; builds the synthetic email from the shop code. `set_active(false)` sets the membership inactive **and** bans the auth user (`ban_duration` long, e.g. `876000h`); `set_active(true)` clears the ban. Safe because riders have one auth user per shop |
| `invite-owner` (POST) | owner or platform admin | Email invite to join a shop as co-owner |
| `platform` (POST) | platform admin | `approve`, `suspend`, `reactivate`, `set_cap`, `create_shop_for` |
| `eta-fallback` | cron secret | Recomputes stale ETAs |

`track` response adds a `shop` object: `{ "name", "slug", "logo_url", "phone", "whatsapp", "till_number", "paybill_number", "paybill_account" }`. Nothing else beyond the fields listed in the earlier design (status, first name, items, payment, destination, rider while on the way, position, ETA).

Rate limits: per-IP on `track`, `track-set-location`, `check_slug` and `rider_shop_lookup`.

## 10. Non-functional

- Tracking page: first content < 3 s on slow 3G; app JS (excluding Mapbox) under ~50 KB gzipped.
- Dashboard works on phone and PC.
- Running costs zero at early volume; per-shop caps keep the shared free tiers safe. Revisit pricing before ~20 active shops.
- UI copy never uses middots (·). Icons: Material Symbols Rounded only.
- WCAG 2.1 AA.

## 11. Acceptance criteria (v1)

- [ ] A new shop signs up, verifies email, sees the setup checklist and a pending banner; cannot send links until approved.
- [ ] Platform admin approves the shop; the owner can then send links.
- [ ] Platform admin creates the first client's shop directly; the owner gets an invite and sets a password.
- [ ] Slug availability check refuses taken and reserved slugs; changing a slug keeps old links working.
- [ ] **Cross-tenant RLS tests:** an owner of shop A cannot read or write any row of shop B through tables, views, RPCs or Realtime; a rider of shop A sees nothing of shop B; anon reads nothing.
- [ ] A rider signs in with shop code + phone + PIN; the same phone can be a rider at two shops with separate PINs.
- [ ] The rider app can tell apart an unknown shop code, a suspended shop, a wrong phone or PIN, and a deactivated rider; a deactivated rider's existing session stops working.
- [ ] A rider can read their own shop row, their profile, and the customers of their open orders, and nothing else beyond their orders; Realtime on `orders` delivers only their own orders.
- [ ] A rider can mark an `on_the_way` order returned; the owner sees it under needs attention and Payments until *Items received*, and the tracking page shows the not-completed state.
- [ ] Tracking page is branded with the shop and shows its till; a wrong slug with a valid token redirects to the right URL.
- [ ] Usage cap blocks new orders with a clear message; usage shows in settings.
- [ ] All single-shop criteria still hold (two-tap location, live map and ETA, rider phone only while on the way, no personal data after 24 h, payments reconciliation, audit timeline, nightly backups).

## 12. Open questions

1. Confirm `fika.co.ke` and the Play Store name "Fika Rider" are available before launch.
2. Pricing for shops after the free period (per month, per delivery, or tiered by cap).
3. Should shops be able to pick a brand colour for their tracking page, or only a logo in v1? (v1 assumption: logo only.)
4. Ownership: the platform and its code stay with the developer; the first client gets a free or discounted shop account. Put this in the agreement.

Decided: tracking page shows the rider's phone only while `on_the_way`. Till/paybill is shown to riders and on the tracking page. Designs use till 12345.