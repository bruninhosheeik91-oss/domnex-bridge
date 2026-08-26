// ============================================================================
// Edge Function: admin-create-access
//
// Criação segura de novos acessos ("Administração → Acessos → Novo acesso").
// Fluxo: DOMNEX_ADMIN autenticado -> app envia JWT + payload -> esta função
// valida o papel do chamador NO SERVIDOR -> resolve/cria o cliente vinculado
// -> cria usuário no Supabase Auth COM SENHA INICIAL (quando fornecida) ou,
// no fluxo legado sem senha, gera convite por e-mail -> ATUALIZA o perfil já
// criado pelo trigger on_auth_user_created_bridge_profile -> responde ao app.
//
// Senha inicial:
//   * A senha é usada UMA única vez aqui, passada direto à Supabase Admin API
//     (auth.admin.createUser). Nunca é gravada em bridge_profiles, nunca é
//     logada e nunca retorna na resposta.
//   * Com senha inicial, o acesso fica utilizável IMEDIATAMENTE (e-mail +
//     senha) — sem depender de convite/SMTP.
//
// Interação com o trigger on_auth_user_created_bridge_profile:
//   * O trigger cria o perfil inicial (CLIENT/PENDING por padrão) a partir de
//     raw_user_meta_data. Esta função NÃO insere outro perfil: após criar o
//     usuário, faz UPDATE do perfil existente com os valores finais pedidos
//     pelo administrador (role/status/client_id/name/email).
//
// Segurança:
//   * service_role key vive APENAS aqui (env do backend Supabase). Nunca vai
//     para dentro do APK.
//   * Chamador precisa ser um bridge_profile com role DOMNEX_ADMIN e status
//     ACTIVE — revalidado aqui, não apenas na interface.
//   * Papéis permitidos: CLIENT | DOMNEX_ADMIN. Status: ACTIVE | PENDING.
//   * Constraint do banco preservada: CLIENT ACTIVE exige client_id real.
//
// Deploy (requer CLI logado):
//   supabase functions deploy admin-create-access --project-ref bknttvuiqsrkftodcsku
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const ALLOWED_ROLES = new Set(["CLIENT", "DOMNEX_ADMIN"]);
const ALLOWED_STATUSES = new Set(["ACTIVE", "PENDING"]);
const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 72; // limite do bcrypt usado pelo GoTrue

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  }

  const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
  const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY");
  if (!SUPABASE_URL || !SERVICE_ROLE_KEY || !ANON_KEY) {
    return json({ error: "SERVER_MISCONFIGURED" }, 500);
  }

  // ------------------------------------------------------------------ caller
  const authHeader = req.headers.get("Authorization") ?? "";
  const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!jwt) return json({ error: "UNAUTHENTICATED" }, 401);

  const callerClient = createClient(SUPABASE_URL, ANON_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });

  const { data: userData, error: userError } = await callerClient.auth.getUser(jwt);
  if (userError || !userData?.user) return json({ error: "UNAUTHENTICATED" }, 401);

  const { data: callerProfile, error: profileError } = await callerClient
    .from("bridge_profiles")
    .select("id, role, status")
    .eq("id", userData.user.id)
    .single();

  if (profileError || !callerProfile) return json({ error: "PROFILE_MISSING" }, 403);
  if (callerProfile.role !== "DOMNEX_ADMIN" || callerProfile.status !== "ACTIVE") {
    return json({ error: "FORBIDDEN" }, 403);
  }

  // ----------------------------------------------------------------- payload
  let payload: {
    name?: string;
    email?: string;
    password?: string;
    role?: string;
    client_name?: string | null;
    status?: string;
  };
  try {
    payload = await req.json();
  } catch (_err) {
    return json({ error: "INVALID_JSON" }, 400);
  }

  const name = (payload.name ?? "").trim();
  const email = (payload.email ?? "").trim().toLowerCase();
  const role = (payload.role ?? "CLIENT").trim().toUpperCase();
  const clientName = (payload.client_name ?? "").trim();
  const status = (payload.status ?? "PENDING").trim().toUpperCase();

  // A senha inicial é OPCIONAL (compatível com o fluxo legado de convite),
  // mas quando enviada precisa atender ao mínimo de segurança.
  const initialPassword =
    typeof payload.password === "string" ? payload.password : "";

  if (name.length < 2) return json({ error: "INVALID_NAME" }, 400);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json({ error: "INVALID_EMAIL" }, 400);
  if (!ALLOWED_ROLES.has(role)) return json({ error: "INVALID_ROLE" }, 400);
  if (!ALLOWED_STATUSES.has(status)) return json({ error: "INVALID_STATUS" }, 400);
  if (
    initialPassword.length > 0 &&
    (initialPassword.length < MIN_PASSWORD_LENGTH ||
      initialPassword.length > MAX_PASSWORD_LENGTH)
  ) {
    return json(
      { error: "INVALID_PASSWORD", detail: `A senha deve ter entre ${MIN_PASSWORD_LENGTH} e ${MAX_PASSWORD_LENGTH} caracteres.` },
      400
    );
  }
  if (role === "CLIENT" && clientName === "") return json({ error: "CLIENT_REQUIRED" }, 400);

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // -------------------------------------------------- cliente vinculado ANTES
  // Resolver o cliente antes de criar o usuário permite validar a regra
  // "CLIENT ACTIVE exige client_id" ANTES de qualquer criação — evitando
  // deixar usuário Auth órfão caso a constraint venha a falhar depois.
  let clientId: string | null = null;
  if (clientName !== "") {
    const { data: existingClient } = await admin
      .from("bridge_clients")
      .select("id")
      .eq("name", clientName)
      .maybeSingle();

    if (existingClient?.id) {
      clientId = existingClient.id as string;
    } else {
      const { data: inserted, error: insertError } = await admin
        .from("bridge_clients")
        .insert({ name: clientName })
        .select("id")
        .single();
      if (!insertError && inserted?.id) clientId = inserted.id as string;
    }
  }

  if (role === "CLIENT" && status === "ACTIVE" && !clientId) {
    return json(
      { error: "CLIENT_REQUIRED", detail: "CLIENT ACTIVE exige um cliente vinculado real." },
      400
    );
  }

  // ------------------------------------------------------------- create user
  const createUserArgs: Record<string, unknown> = {
    email,
    email_confirm: initialPassword.length > 0, // com senha definida pelo admin, login imediato
    user_metadata: {
      name,
      role,
      ...(clientName ? { client_name: clientName } : {}),
    },
  };
  if (initialPassword.length > 0) {
    // Criação administrativa REAL com senha inicial (sem convite).
    createUserArgs.password = initialPassword;
  }

  const { data: created, error: createError } = await admin.auth.admin.createUser(
    createUserArgs as Parameters<typeof admin.auth.admin.createUser>[0]
  );

  if (createError || !created?.user) {
    const code = (createError as { code?: string } | null)?.code ?? "";
    if (code === "email_exists" || /already|registered|exists/i.test(createError?.message ?? "")) {
      return json({ error: "EMAIL_IN_USE" }, 409);
    }
    return json({ error: "USER_CREATE_FAILED", detail: createError?.message ?? null }, 400);
  }

  // --------------------------------------------------------------- profile
  // O trigger on_auth_user_created_bridge_profile já criou o perfil básico
  // (CLIENT/PENDING). Aqui apenas ATUALIZAMOS esse perfil para os valores
  // finais — nenhum INSERT duplicado.
  const { error: updateError } = await admin
    .from("bridge_profiles")
    .update({
      name,
      email,
      role,
      client_id: clientId,
      status,
    })
    .eq("id", created.user.id);

  if (updateError) {
    return json(
      { user_id: created.user.id, error: "PROFILE_UPDATE_FAILED", detail: updateError.message },
      207
    );
  }

  // ------------------------------------------------------------ invite link
  // Fluxo LEGADO (sem senha inicial): envia convite para o usuário definir a
  // própria senha. Requer SMTP configurado no projeto (Authentication → Emails).
  let inviteSent: boolean | null = null;
  let inviteError: string | null = null;
  if (initialPassword.length === 0) {
    const { error: linkError } = await admin.auth.admin.generateLink({
      type: "invite",
      email,
    });
    inviteSent = !linkError;
    inviteError = linkError ? linkError.message : null;
  }

  return json(
    {
      user_id: created.user.id,
      email,
      role,
      client_name: clientName === "" ? null : clientName,
      status,
      ...(inviteSent === null ? {} : { invite_sent: inviteSent, invite_error: inviteError }),
    },
    201
  );
});
