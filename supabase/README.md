# Supabase — DOMNEX BRIDGE (FASE 6A)

Autenticação real do app via **Supabase Auth**. Projeto vinculado:
`mwponxhscdtxhcdyxeww` (ver `supabase/.temp/linked-project.json`).

## O que existe aqui

| Caminho | Papel |
| --- | --- |
| `migrations/20260823000000_fase6a_bridge_auth.sql` | Tabelas `bridge_clients` e `bridge_profiles`, enums, RLS, trigger de provisionamento, RPC `bridge_admin_set_user_status` |
| `migrations/20260823120000_fix_bridge_profiles_client_constraint.sql` | Corretiva da constraint de vínculo (ADMIN sem cliente; CLIENT PENDING pode ficar sem; ACTIVE/SUSPENDED exige) |
| `migrations/20260824000000_fase6b_bridge_admin_update_access.sql` | RPC segura `bridge_admin_update_access`: edição administrativa de nome/perfil/status/cliente com validação no servidor |
| `functions/admin-create-access/index.ts` | Edge Function segura para criar acessos (valida role no servidor; usa service_role só no backend) |
| `functions/admin-update-email/index.ts` | Edge Function segura para alterar e-mail de um acesso via `auth.admin` (e-mail pertence ao Supabase Auth, não a `bridge_profiles`) |
| `docs/ADMIN_BOOTSTRAP.md` | Como transformar um usuário real do Supabase Auth em `DOMNEX_ADMIN` (sem hardcode no APK) |

## Segurança (regras fixas)

- **Nunca** colocar `service_role key`, senhas administrativas ou segredos privados
  no APK/repo.
- Apenas `SUPABASE_URL` + `SUPABASE_ANON_KEY` (públicas por design) entram no APK,
  via `local.properties` (gitignored) ou variáveis de ambiente — ver `app/build.gradle.kts`.
- Senhas pertencem exclusivamente ao Supabase Auth (`auth.users`). Nenhuma tabela Bridge guarda senha.

## Configuração local (necessária para o app usar o backend real)

Em `local.properties` (raiz do projeto, já ignorado pelo git):

```properties
SUPABASE_URL=https://mwponxhscdtxhcdyxeww.supabase.co
SUPABASE_ANON_KEY=<anon/publishable key do projeto>
```

Alternativa: variáveis de ambiente `SUPABASE_URL` / `SUPABASE_ANON_KEY`.

Sem essas chaves: build DEBUG usa `LocalAuthGateway`/`LocalUserDirectory` (DEV);
build RELEASE exige as chaves (login retorna "não configurado" caso faltem).

## Aplicar a migration

```bash
supabase db push
```

## Deploy das Edge Functions

```bash
supabase functions deploy admin-create-access --project-ref mwponxhscdtxhcdyxeww
supabase functions deploy admin-update-email  --project-ref mwponxhscdtxhcdyxeww
```

No projeto hospedado, `SUPABASE_URL`, `SUPABASE_ANON_KEY` e
`SUPABASE_SERVICE_ROLE_KEY` já são injetados automaticamente no runtime das
Edge Functions (sem configurá-las manualmente). Para SMTP dos convites,
configure em **Authentication → Emails**.

## Conta administrativa real

Siga `docs/ADMIN_BOOTSTRAP.md`. O código não cria nem hardcode a conta admin.

## Pendências manuais (fora do APK)

1. `supabase db push` das migrations (inclui a FASE 6B).
2. `supabase functions deploy admin-create-access` e `admin-update-email`.
3. Configurar SMTP/convites (Authentication → Emails) — sem isso, convites e
   redefinições de senha são aceitos pelo backend mas o e-mail não é entregue.
4. Bootstrap da conta `DOMNEX_ADMIN` real (`docs/ADMIN_BOOTSTRAP.md`).
5. Preencher `SUPABASE_URL` + `SUPABASE_ANON_KEY` no ambiente de build oficial.
