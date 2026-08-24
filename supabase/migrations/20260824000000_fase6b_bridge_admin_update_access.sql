-- ============================================================================
-- FASE 6B — Edição administrativa de acessos (nome, perfil, status, cliente)
--
-- Complementa a FASE 6A. Enquanto `bridge_admin_set_user_status` continua
-- válida para suspender/reabilitar, esta RPC permite edição ATÔMICA dos campos
-- administrativos do acesso, com validação de papel NO SERVIDOR e mensagens de
-- erro claras (sem depender da UI):
--
--   * Chamador precisa ser bridge_profile DOMNEX_ADMIN + ACTIVE.
--   * Alvo precisa existir.
--   * Admin não altera o próprio papel/status (evita apagar a última admin).
--   * Regras de vínculo (espelham a constraint da tabela):
--       - DOMNEX_ADMIN não tem client_id;
--       - CLIENT PENDING pode ficar sem client_id;
--       - CLIENT ACTIVE/SUSPENDED exige client_id real em bridge_clients.
--
-- Aplicar manualmente: supabase db push  (NÃO é executado automaticamente)
-- ============================================================================

create or replace function public.bridge_admin_update_access(
    p_target_user_id uuid,
    p_name           text default null,
    p_role           public.bridge_user_role default null,
    p_status         public.bridge_user_status default null,
    p_client_id      uuid default null,
    p_clear_client   boolean default false
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_target    public.bridge_profiles%rowtype;
    v_name      text;
    v_role      public.bridge_user_role;
    v_status    public.bridge_user_status;
    v_client_id uuid;
begin
    -- ---------------------------------------------------------------- chamador
    if not public.bridge_is_active_domnex_admin() then
        raise exception 'Somente DOMNEX_ADMIN ativo pode editar acessos.'
            using errcode = '42501'; -- insufficient_privilege
    end if;

    -- ------------------------------------------------------------------- alvo
    select * into v_target
    from public.bridge_profiles
    where id = p_target_user_id;

    if not found then
        raise exception 'Perfil não encontrado.' using errcode = 'P0002';
    end if;

    ------------------------------------------------------------- auto-bloqueio
    -- Um administrador não altera o próprio papel/status: evita deixar a
    -- organização sem nenhum DOMNEX_ADMIN ativo (perda de acesso ao painel).
    if v_target.id = auth.uid() and (p_role is not null or p_status is not null) then
        raise exception 'Não é permitido alterar o próprio perfil ou status.'
            using errcode = '42501';
    end if;

    -- ------------------------------------------------------------ parâmetros
    if p_name is not null and length(btrim(p_name)) < 2 then
        raise exception 'Nome inválido.' using errcode = '22000';
    end if;

    -- Estado final pretendido: NULL = manter o valor atual do alvo.
    v_name      := coalesce(btrim(p_name), v_target.name);
    v_role      := coalesce(p_role, v_target.role);
    v_status    := coalesce(p_status, v_target.status);
    v_client_id := case
        when p_clear_client then null
        when p_client_id is not null then p_client_id
        else v_target.client_id
    end;

    -- O cliente informado precisa existir de fato.
    if v_client_id is not null
       and not exists (
           select 1 from public.bridge_clients c where c.id = v_client_id
       ) then
        raise exception 'Cliente não encontrado.' using errcode = 'P0002';
    end if;

    -- ------------------------------------------- regras de vínculo (mensagem clara)
    if v_role = 'DOMNEX_ADMIN' and v_client_id is not null then
        raise exception 'DOMNEX_ADMIN não deve ter cliente vinculado.'
            using errcode = '23514'; -- check_violation
    end if;

    if v_role = 'CLIENT'
       and v_status in ('ACTIVE', 'SUSPENDED')
       and v_client_id is null then
        raise exception 'CLIENT ACTIVE/SUSPENDED exige cliente vinculado.'
            using errcode = '23514';
    end if;

    -- ------------------------------------------------------------------ update
    update public.bridge_profiles
    set name      = v_name,
        role      = v_role,
        status    = v_status,
        client_id = v_client_id
    where id = p_target_user_id;
end;
$$;

revoke all on function public.bridge_admin_update_access(uuid, text, public.bridge_user_role, public.bridge_user_status, uuid, boolean)
    from public, anon;

grant execute on function public.bridge_admin_update_access(uuid, text, public.bridge_user_role, public.bridge_user_status, uuid, boolean)
    to authenticated;
