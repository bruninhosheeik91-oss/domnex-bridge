# Bootstrap da conta DOMNEX_ADMIN (conta administrativa real)

> Nenhuma conta administrativa é criada ou hardcoded no APK.
> O e-mail e a senha reais do administrador são cadastrados **fora do app**,
> direto no Supabase. Este guia transforma um usuário já existente do
> Supabase Auth em `DOMNEX_ADMIN`.

## Pré-requisitos

1. Projeto Supabase vinculado (CLI):
   ```bash
   supabase link --project-ref bknttvuiqsrkftodcsku
   ```
2. Migration aplicada:
   ```bash
   supabase db push
   ```

## Passo 1 — Criar o usuário administrador (fora do APK)

No Dashboard do Supabase: **Authentication → Users → Add user → Create new user**
(e-mail + senha reais). Ou via CLI/SQL com `auth.admin` de sua preferência.

O trigger `on_auth_user_created_bridge_profile` (migration FASE 6A) cria
automaticamente o `bridge_profiles` correspondente como `CLIENT`/`PENDING`.

*(Alternativa: se criar o usuário via SQL no banco, o trigger também dispara,
pois atua na tabela `auth.users`.)*

## Passo 2 — Criar o cliente (opcional)

Se o administrador não pertence a um cliente (é o padrão), não é necessário
vínculo. Para criar clientes que serão usados pelos usuários CLIENT:

```sql
insert into public.bridge_clients (name) values ('Nome do Cliente');
```

## Passo 3 — Promover a DOMNEX_ADMIN

Substitua `<SEU_EMAIL>` pelo e-mail real cadastrado no passo 1 e execute no
**SQL Editor** do Supabase:

```sql
update public.bridge_profiles
set role = 'DOMNEX_ADMIN',
    status = 'ACTIVE',
    client_id = null
where email = '<SEU_EMAIL>';
```

Verificação:

```sql
select id, name, email, role, status
from public.bridge_profiles
where email = '<SEU_EMAIL>';
-- role deve ser DOMNEX_ADMIN e status ACTIVE
```

## Passo 4 — Validar login

No app (build com `SUPABASE_URL` + `SUPABASE_ANON_KEY` configurados), faça login
com esse e-mail/senha. O roteamento leva direto à área
**Administração Domnex Bridge** (`RouteTarget.ADMIN_HOME`).

Nunca compartilhe essa conta; para operações cotidianas use contas `CLIENT`
ou crie novos `DOMNEX_ADMIN` pela tela **Administração → Acessos → Novo acesso**
(que exige a Edge Function `admin-create-access` implantada).
