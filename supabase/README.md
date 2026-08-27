# Supabase — DOMNEX BRIDGE (FASE 6A)

Autenticação real do app via **Supabase Auth**. Projeto vinculado:
`bknttvuiqsrkftodcsku` (ver `supabase/.temp/linked-project.json`).

## O que existe aqui

| Caminho | Papel |
| --- | --- |
| `migrations/20260823000000_fase6a_bridge_auth.sql` | Tabelas `bridge_clients` e `bridge_profiles`, enums, RLS, trigger de provisionamento, RPC `bridge_admin_set_user_status` |
| `migrations/20260823120000_fix_bridge_profiles_client_constraint.sql` | Corretiva da constraint de vínculo (ADMIN sem cliente; CLIENT PENDING pode ficar sem; ACTIVE/SUSPENDED exige) |
| `migrations/20260824000000_fase6b_bridge_admin_update_access.sql` | RPC segura `bridge_admin_update_access`: edição administrativa de nome/perfil/status/cliente com validação no servidor |
| `functions/admin-create-access/index.ts` | Edge Function segura para criar acessos (valida role no servidor; usa service_role só no backend). Suporta **senha inicial** opcional: cria o usuário já com login utilizável (e-mail + senha, sem depender de convite/SMTP) e depois apenas ATUALIZA o perfil criado pelo trigger — nunca insere um segundo perfil nem ecoa a senha |
| `functions/admin-update-email/index.ts` | Edge Function segura para alterar e-mail de um acesso via `auth.admin` (e-mail pertence ao Supabase Auth, não a `bridge_profiles`) |
| `functions/admin-delete-client/index.ts` | Edge Function segura para a **exclusão definitiva** de um cliente ("Zona de risco"): remove os usuários Auth vinculados (login cessa imediatamente; perfis caem por FK ON DELETE CASCADE) e só então a linha em `bridge_clients`. Guardas: auto-exclusão e perfis DOMNEX_ADMIN bloqueados; falha parcial NÃO exclui o cliente |
| `functions/bridge-monitoring-proxy/index.ts` | Proxy seguro (`verify_jwt = true` em `config.toml`) do **monitoramento de bridges** do CFI. Exige chamador `bridge_profiles` `DOMNEX_ADMIN` + `ACTIVE` e faz a chamada server-to-server ao CFI usando `M2M_MONITORING_SECRET` — nunca exposto ao Android. Necessita as envs `M2M_MONITORING_SECRET` e `CFI_MONITORING_URL`; sem elas responde 503 (fail-closed) |
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
SUPABASE_URL=https://bknttvuiqsrkftodcsku.supabase.co
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
supabase functions deploy admin-create-access --project-ref bknttvuiqsrkftodcsku
supabase functions deploy admin-update-email  --project-ref bknttvuiqsrkftodcsku
supabase functions deploy admin-delete-client --project-ref bknttvuiqsrkftodcsku
supabase functions deploy bridge-monitoring-proxy --project-ref bknttvuiqsrkftodcsku
```

No projeto hospedado, `SUPABASE_URL`, `SUPABASE_ANON_KEY` e
`SUPABASE_SERVICE_ROLE_KEY` já são injetados automaticamente no runtime das
Edge Functions (sem configurá-las manualmente). Para SMTP dos convites,
configure em **Authentication → Emails**.

### bridge-monitoring-proxy — envs necessárias

Além de `SUPABASE_URL`/`SUPABASE_ANON_KEY` (automáticas), a proxy exige duas
envs configuradas em **Settings → Edge Functions** do projeto DOMNEX BRIDGE
(`bknttvuiqsrkftodcsku`):

- `M2M_MONITORING_SECRET` — secret M2M usado no cabeçalho `Authorization: Bearer`
  na chamada server-to-server ao CFI. **Nunca** vai para dentro do APK.
- `CFI_MONITORING_URL` — URL do endpoint de monitoramento no CFI:
  `https://xfvdqbuwqzenxdvtiqqd.supabase.co/functions/v1/bridge-monitoring`

Sem esses dois valores a função responde `503` (fail-closed), sem expor
detalhes. `verify_jwt = true` está fixado em `supabase/config.toml`.

## Conta administrativa real

Siga `docs/ADMIN_BOOTSTRAP.md`. O código não cria nem hardcode a conta admin.

## Pendências manuais (fora do APK)

1. `supabase db push` das migrations (inclui a FASE 6B).
2. `supabase functions deploy admin-create-access`, `admin-update-email` e
   `admin-delete-client`.
3. Configurar SMTP/convites (Authentication → Emails) — sem isso, convites e
   redefinições de senha são aceitos pelo backend mas o e-mail não é entregue.
4. Bootstrap da conta `DOMNEX_ADMIN` real (`docs/ADMIN_BOOTSTRAP.md`).
5. Preencher `SUPABASE_URL` + `SUPABASE_ANON_KEY` no ambiente de build oficial.
