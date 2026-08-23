-- ============================================================================
-- Corretiva FASE 6A — constraint bridge_profiles_client_required_for_client_role
--
-- Problema: o trigger de provisionamento (on_auth_user_created_bridge_profile)
-- cria todo novo auth.user como role = 'CLIENT', status = 'PENDING',
-- client_id = null. A constraint antiga exigia client_id para QUALQUER CLIENT,
-- fazendo o INSERT do perfil falhar e bloqueando a criação do usuário.
--
-- Regra correta:
--   * DOMNEX_ADMIN pode existir sem client_id;
--   * CLIENT com status PENDING pode existir sem client_id;
--   * CLIENT ACTIVE/SUSPENDED somente quando houver client_id.
--
-- Aplicar manualmente: supabase db push
-- ============================================================================

alter table public.bridge_profiles
    drop constraint if exists bridge_profiles_client_required_for_client_role;

alter table public.bridge_profiles
    add constraint bridge_profiles_client_required_for_client_role
    check (
        client_id is not null
        or role = 'DOMNEX_ADMIN'
        or (role = 'CLIENT' and status = 'PENDING')
    );
