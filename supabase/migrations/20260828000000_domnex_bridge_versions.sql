-- ============================================================================
-- DOMNEX BRIDGE — Tabela oficial de versões (fonte de verdade para updates)
--
-- O aplicativo consulta esta tabela via REST (chave anon/publishable) para
-- saber a versão mais recente publicada, a mínima suportada, a URL oficial do
-- APK, release notes, se a atualização é obrigatória e a data de publicação.
--
-- Princípios:
--   * Só releases com `published = true` são visíveis ao aplicativo.
--   * A release mais recente é a de MAIOR `version_code` (nunca version_name).
--   * `apk_url` deve ser HTTPS.
--   * NÃO há nenhuma URL/APK fictício nem release publicada aqui: a tabela
--     nasce vazia. As linhas são inseridas pelo dashboard/admnistrador quando
--     existir URL HTTPS oficial.
--   * RLS habilitado com policy SOMENTE DE LEITURA para releases publicadas.
--     Nenhuma escrita via anon. A administração ocorre fora do aplicativo.
--
-- Aplicar manualmente: supabase db push  (NÃO é executado automaticamente)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Tabela
-- ---------------------------------------------------------------------------
create table public.domnex_bridge_versions (
    id                    uuid primary key default gen_random_uuid(),
    version_name          text not null,
    version_code          integer not null unique,
    minimum_version_code  integer not null default 1,
    apk_url               text not null,
    release_notes         text,
    mandatory             boolean not null default false,
    published             boolean not null default false,
    published_at          timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    constraint domnex_bridge_versions_apk_https
        check (apk_url ~* '^https://')
);

create index domnex_bridge_versions_published_code_idx
    on public.domnex_bridge_versions (published, version_code desc);

-- ---------------------------------------------------------------------------
-- updated_at automático (mesmo helper já existente no projeto)
-- ---------------------------------------------------------------------------
create trigger trg_domnex_bridge_versions_touch_updated_at
    before update on public.domnex_bridge_versions
    for each row execute function public.bridge_touch_updated_at();

-- ---------------------------------------------------------------------------
-- RLS — somente leitura das releases publicadas para o app (anon)
-- ---------------------------------------------------------------------------
alter table public.domnex_bridge_versions enable row level security;
alter table public.domnex_bridge_versions force row level security;

-- A chave anon/publishable (usada pelo aplicativo) só LÊ releases publicadas.
create policy "domnex_bridge_versions_select_published"
    on public.domnex_bridge_versions
    for select
    to anon
    using (published = true);

-- DOMNEX_ADMIN autenticado pode ler todas (painel/diagnóstico) — leitura apenas.
create policy "domnex_bridge_versions_select_admin_all"
    on public.domnex_bridge_versions
    for select
    to authenticated
    using (public.bridge_is_active_domnex_admin());

-- NENHUMA policy de INSERT/UPDATE/DELETE pública. Administração da tabela é
-- feita pelo dashboard Supabase / backend administrativo (superando RLS), nunca
-- pelo aplicativo.

-- ---------------------------------------------------------------------------
-- Grants diretos (defesa em profundidade; RLS continua valendo sempre)
-- ---------------------------------------------------------------------------
grant select on public.domnex_bridge_versions to anon, authenticated;

-- Revoga explicitamente qualquer privilégio de escrita concedido por padrão.
revoke insert, update, delete on public.domnex_bridge_versions from anon, authenticated, public;
