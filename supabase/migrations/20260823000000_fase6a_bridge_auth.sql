-- ============================================================================
-- FASE 6A — DOMNEX BRIDGE: autenticação real (Supabase Auth)
--
-- Estruturas de perfil/cliente + RLS + provisionamento automático + RPC admin.
--
-- Princípios:
--   * Senhas pertencem EXCLUSIVAMENTE ao Supabase Auth (auth.users). Nenhuma
--     tabela aqui guarda senha, hash ou segredo.
--   * Papéis permitidos: apenas CLIENT e DOMNEX_ADMIN.
--   * Segurança no banco (RLS), nunca só na interface.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Tipos enumerados
-- ---------------------------------------------------------------------------
create type public.bridge_user_role as enum ('CLIENT', 'DOMNEX_ADMIN');
create type public.bridge_user_status as enum ('ACTIVE', 'PENDING', 'SUSPENDED');

-- ---------------------------------------------------------------------------
-- bridge_clients — clientes (empresas) atendidos pela Domnex
-- ---------------------------------------------------------------------------
create table public.bridge_clients (
    id          uuid primary key default gen_random_uuid(),
    name        text not null unique,
    status      public.bridge_user_status not null default 'ACTIVE',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- bridge_profiles — perfil 1:1 com auth.users (sem duplicar senha)
-- ---------------------------------------------------------------------------
create table public.bridge_profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    name        text not null default '',
    email       text not null unique,
    role        public.bridge_user_role not null default 'CLIENT',
    client_id   uuid references public.bridge_clients (id) on delete set null,
    status      public.bridge_user_status not null default 'PENDING',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint bridge_profiles_client_required_for_client_role
        check (role = 'DOMNEX_ADMIN' or client_id is not null)
);

create index bridge_profiles_client_id_idx on public.bridge_profiles (client_id);
create index bridge_profiles_role_idx      on public.bridge_profiles (role);
create index bridge_clients_name_lower_idx on public.bridge_clients (lower(name));

-- ---------------------------------------------------------------------------
-- updated_at automático
-- ---------------------------------------------------------------------------
create or replace function public.bridge_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger trg_bridge_clients_touch_updated_at
    before update on public.bridge_clients
    for each row execute function public.bridge_touch_updated_at();

create trigger trg_bridge_profiles_touch_updated_at
    before update on public.bridge_profiles
    for each row execute function public.bridge_touch_updated_at();

-- ---------------------------------------------------------------------------
-- Helper: chamador é um DOMNEX_ADMIN ativo? (SECURITY DEFINER para uso em policies)
-- ---------------------------------------------------------------------------
create or replace function public.bridge_is_active_domnex_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.bridge_profiles p
        where p.id = auth.uid()
          and p.role = 'DOMNEX_ADMIN'
          and p.status = 'ACTIVE'
    );
$$;

revoke all on function public.bridge_is_active_domnex_admin() from public, anon;

-- ---------------------------------------------------------------------------
-- RLS — habilitar em todas as tabelas Bridge
-- ---------------------------------------------------------------------------
alter table public.bridge_clients  enable row level security;
alter table public.bridge_profiles enable row level security;
alter table public.bridge_clients  force row level security;
alter table public.bridge_profiles force row level security;

-- CLIENT vê somente o próprio perfil -----------------------------------------
create policy "bridge_profiles_select_own"
    on public.bridge_profiles
    for select
    to authenticated
    using (id = auth.uid());

-- DOMNEX_ADMIN ativo enxerga todos os perfis (área administrativa) ------------
create policy "bridge_profiles_select_admin_all"
    on public.bridge_profiles
    for select
    to authenticated
    using (public.bridge_is_active_domnex_admin());

-- Somente DOMNEX_ADMIN ativo altera perfis (status/cliente/vínculos).
-- Usuário comum NÃO se edita: mudanças passam pelo backend/admin.
create policy "bridge_profiles_update_admin_only"
    on public.bridge_profiles
    for update
    to authenticated
    using (public.bridge_is_active_domnex_admin())
    with check (public.bridge_is_active_domnex_admin());

-- Sem INSERT/DELETE por app: criação via backend privilegiado (service_role /
-- Edge Function) que ignora RLS; exclusão não faz parte do fluxo (suspende-se).

-- CLIENT vê somente o próprio cliente ----------------------------------------
create policy "bridge_clients_select_own_member"
    on public.bridge_clients
    for select
    to authenticated
    using (
        id in (
            select p.client_id
            from public.bridge_profiles p
            where p.id = auth.uid()
              and p.client_id is not null
        )
    );

-- DOMNEX_ADMIN ativo enxerga todos os clientes -------------------------------
create policy "bridge_clients_select_admin_all"
    on public.bridge_clients
    for select
    to authenticated
    using (public.bridge_is_active_domnex_admin());

-- ---------------------------------------------------------------------------
-- Provisionamento automático: todo novo auth.user recebe bridge_profile
-- ---------------------------------------------------------------------------
create or replace function public.bridge_handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_role   public.bridge_user_role := 'CLIENT';
    v_status public.bridge_user_status := 'PENDING';
    v_client uuid;
begin
    if new.raw_user_meta_data ->> 'role' = 'DOMNEX_ADMIN' then
        v_role := 'DOMNEX_ADMIN';
    end if;

    if coalesce(new.raw_user_meta_data ->> 'client_name', '') <> '' then
        select c.id into v_client
        from public.bridge_clients c
        where c.name = new.raw_user_meta_data ->> 'client_name'
        limit 1;

        if v_client is null then
            insert into public.bridge_clients (name)
            values (new.raw_user_meta_data ->> 'client_name')
            on conflict (name) do nothing;

            select c.id into v_client
            from public.bridge_clients c
            where c.name = new.raw_user_meta_data ->> 'client_name'
            limit 1;
        end if;
    end if;

    insert into public.bridge_profiles (id, name, email, role, client_id, status)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'name', ''),
        new.email,
        v_role,
        v_client,
        v_status
    )
    on conflict (id) do nothing;

    return new;
end;
$$;

create trigger on_auth_user_created_bridge_profile
    after insert on auth.users
    for each row execute function public.bridge_handle_new_auth_user();

-- ---------------------------------------------------------------------------
-- RPC segura: DOMNEX_ADMIN suspende/reabilita acessos
-- (usada por RemoteUserDirectory.setStatus via PostgREST /rpc/...)
-- ---------------------------------------------------------------------------
create or replace function public.bridge_admin_set_user_status(
    p_user_id uuid,
    p_new_status public.bridge_user_status
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.bridge_is_active_domnex_admin() then
        raise exception 'Somente DOMNEX_ADMIN ativo pode alterar status.'
            using errcode = '42501'; -- insufficient_privilege
    end if;

    update public.bridge_profiles
    set status = p_new_status
    where id = p_user_id;

    if not found then
        raise exception 'Perfil não encontrado.' using errcode = 'P0002';
    end if;
end;
$$;

revoke all on function public.bridge_admin_set_user_status(uuid, public.bridge_user_status)
    from public, anon;
grant execute on function public.bridge_admin_set_user_status(uuid, public.bridge_user_status)
    to authenticated;

-- ---------------------------------------------------------------------------
-- Grants diretos (defesa em profundidade; RLS continua valendo sempre)
-- ---------------------------------------------------------------------------
grant select on public.bridge_profiles to authenticated;
grant select on public.bridge_clients  to authenticated;
