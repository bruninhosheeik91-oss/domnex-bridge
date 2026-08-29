-- ============================================================================
-- DOMNEX BRIDGE — provisioning automático pós-reinstalação
--
-- Audita da estrutura atual:
--   * bridge_profiles (id -> auth.users, role, status, client_id) já identifica
--     o usuário e o cliente/organização dele — REUTILIZADO como está.
--   * NÃO existia nenhuma tabela de credenciais/configuração do Bridge
--     (endpoint + token). O endpoint/token viviam apenas no SharedPreferences
--     do dispositivo (cfi_bridge_prefs), configurados manualmente na tela
--     técnica — e eram perdidos na desinstalação.
--
-- Esta migration cria a ÚNICA tabela nova necessária: a configuração
-- operacional por CLIENTE (1:1 com bridge_clients). Não duplica bridge_clients
-- nem bridge_profiles; apenas amarra o endpoint/token à organização.
--
-- Segurança (regra fixa):
--   * RLS FORCE habilitado e SEM NENHUMA policy de leitura/escrita pública —
--     nem mesmo `authenticated` lê estas linhas diretamente via REST. O único
--     caminho é a Edge Function `bridge-provisioning` (valida o JWT com
--     auth.getUser, confere bridge_profile ACTIVE e só então lê com
--     service_role, que vive apenas no backend).
--   * O bridge_token aqui é o TOKEN OPERACIONAL de ingestão do cliente (o
--     mesmo que o app usava para enviar vendas). NUNCA é service_role, NUNCA é
--     M2M_MONITORING_SECRET e NUNCA é senha administrativa.
--   * Registro/edição da configuração é feito fora do app (dashboard/SQL com
--     privilégio elevado) — mesmo padrão de domnex_bridge_versions.
--
-- Como registrar a config de um cliente (exemplo, fora do APK):
--   insert into public.bridge_configs (client_id, target_system_name, api_base_url, bridge_token)
--   select id, 'CFI', 'https://cfi.example.com/functions/v1/ingest', '<token-operacional>'
--   from public.bridge_clients where name = 'Nome do Cliente';
--
-- Aplicar manualmente: supabase db push  (NÃO é executado automaticamente)
-- ============================================================================

create table public.bridge_configs (
    id                 uuid primary key default gen_random_uuid(),
    client_id          uuid not null unique references public.bridge_clients (id) on delete cascade,
    target_system_name text not null default '',
    api_base_url       text not null,
    bridge_token       text not null,
    enabled            boolean not null default true,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint bridge_configs_https_url
        check (api_base_url ~* '^https://'),
    constraint bridge_configs_token_not_blank
        check (btrim(bridge_token) <> '')
);

-- updated_at automático (mesmo helper já existente no projeto)
create trigger trg_bridge_configs_touch_updated_at
    before update on public.bridge_configs
    for each row execute function public.bridge_touch_updated_at();

-- ---------------------------------------------------------------------------
-- RLS — força total e SEM policies: ninguém lê/escreve via REST.
-- A Edge Function bridge-provisioning valida o chamador e lê via service_role.
-- ---------------------------------------------------------------------------
alter table public.bridge_configs enable row level security;
alter table public.bridge_configs force row level security;

-- NENHUMA policy de select/insert/update/delete para anon/authenticated/public.
-- Revoga explicitamente qualquer privilégio herdado por padrão (defesa em
-- profundidade; com FORCE RLS e zero policies, o acesso direto já é negado).
revoke all on public.bridge_configs from anon, authenticated, public;

-- O backend privilegiado (service_role nas Edge Functions) mantém acesso total;
-- service_role nunca vai para dentro do APK.
grant all on public.bridge_configs to service_role;