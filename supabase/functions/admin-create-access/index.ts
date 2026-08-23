// ============================================================================
// Edge Function: admin-create-access
//
// Criação segura de novos acessos ("Administração → Acessos → Novo acesso").
// Fluxo: DOMNEX_ADMIN autenticado -> app envia JWT + payload -> esta função
// valida o papel do chamador NO SERVIDOR -> cria usuário no Supabase Auth
// (sem senha; usuário define a própria senha via convite) -> grava/atualiza
// bridge_profiles -> responde ao app.
//
// Segurança:
//   * service_role key vive APENAS aqui (env do backend Supabase). Nunca vai
//     para dentro do APK.
//   * Chamador precisa ser um bridge_profile com role DOMNEX_ADMIN e status
//     ACTIVE — revalidado aqui, não apenas na interface.
//   * Papéis permitidos: CLIENT | DOMNEX_ADMIN.
//
// Deploy (requer CLI logado):
//   supabase functions deploy admin-create-access --project-ref mwponxhscdtxhcdyxeww
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const ALLOWED_ROLES = new Set(["CLIENT", "DOMNEX_ADMIN"]);
const ALLOWED_STATUSES = new Set(["ACTIVE", "PENDING"]);

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

  if (name.length < 2) return json({ error: "INVALID_NAME" }, 400);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return json({ error: "INVALID_EMAIL" }, 400);
  if (!ALLOWED_ROLES.has(role)) return json({ error: "INVALID_ROLE" }, 400);
  if (!ALLOWED_STATUSES.has(status)) return json({ error: "INVALID_STATUS" }, 400);
  if (role === "CLIENT" && clientName === "") return json({ error: "CLIENT_REQUIRED" }, 400);

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // ------------------------------------------------------------- create user
  // Sem senha: o usuário define a própria senha pelo link de convite.
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email,
    email_confirm: false,
    user_metadata: {
      name,
      role,
      ...(clientName ? { client_name: clientName } : {}),
    },
  });

  if (createError || !created?.user) {
    const code = (createError as { code?: string } | null)?.code ?? "";
    if (code === "email_exists" || /already|registered|exists/i.test(createError?.message ?? "")) {
      return json({ error: "EMAIL_IN_USE" }, 409);
    }
    return json({ error: "USER_CREATE_FAILED", detail: createError?.message ?? null }, 400);
  }

  // --------------------------------------------------------------- profile
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

  // O trigger on_auth_user_created_bridge_profile já criou o perfil básico;
  // aqui alinhamos nome/role/cliente/status escolhidos pelo administrador.
  const { error: upsertError } = await admin.from("bridge_profiles").upsert({
    id: created.user.id,
    name,
    email,
    role,
    client_id: clientId,
    status,
  });

  if (upsertError) {
    return json(
      { user_id: created.user.id, error: "PROFILE_UPSERT_FAILED", detail: upsertError.message },
      207
    );
  }

  // ------------------------------------------------------------ invite link
  // Envia e-mail de convite para o usuário definir a própria senha.
  // Requer SMTP configurado no projeto Supabase (Authentication → Emails).
  const { error: linkError } = await admin.auth.admin.generateLink({
    type: "invite",
    email,
  });

  return json(
    {
      user_id: created.user.id,
      email,
      role,
      client_name: clientName === "" ? null : clientName,
      status,
      invite_sent: !linkError,
      invite_error: linkError ? linkError.message : null,
    },
    201
  );
});
