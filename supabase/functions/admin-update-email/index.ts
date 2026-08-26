// ============================================================================
// Edge Function: admin-update-email
//
// Alteração ADMINISTRATIVA do e-mail de um acesso. O e-mail pertence ao
// Supabase Auth (auth.users) — NÃO basta editar bridge_profiles.email.
// Somente auth.admin consegue alterá-lo; por isso a operação vive AQUI,
// no backend, onde a service_role key é injetada pelo runtime (nunca no APK).
//
// Fluxo: DOMNEX_ADMIN autenticado -> app envia JWT + { user_id, email } ->
// esta função valida o papel do chamador NO SERVIDOR -> auth.admin.updateUserById
// -> sincroniza bridge_profiles.email -> responde ao app.
//
// Segurança:
//   * service_role key vive APENAS aqui (env do backend Supabase).
//   * Chamador precisa ser bridge_profile role DOMNEX_ADMIN + status ACTIVE —
//     revalidado aqui, não apenas na interface.
//   * email_confirm: true — alteração administrativa é considerada verificada
//     (evita deixar o usuário preso aguardando um e-mail de confirmação que
//     pode não chegar sem SMTP configurado).
//
// Deploy (requer CLI logado; NÃO executar automaticamente):
//   supabase functions deploy admin-update-email --project-ref bknttvuiqsrkftodcsku
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

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

  const { data: userData, error: userError } = await callerClient.auth.getUser(
    jwt,
  );
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
  let payload: { user_id?: string; email?: string };
  try {
    payload = await req.json();
  } catch (_err) {
    return json({ error: "INVALID_JSON" }, 400);
  }

  const userId = (payload.user_id ?? "").trim();
  const email = (payload.email ?? "").trim().toLowerCase();

  if (!UUID_RE.test(userId)) return json({ error: "INVALID_USER_ID" }, 400);
  if (!EMAIL_RE.test(email)) return json({ error: "INVALID_EMAIL" }, 400);

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // O alvo precisa existir como perfil Bridge antes de tocar no Auth.
  const { data: targetProfile } = await admin
    .from("bridge_profiles")
    .select("id")
    .eq("id", userId)
    .maybeSingle();

  if (!targetProfile?.id) return json({ error: "PROFILE_MISSING" }, 404);

  // ------------------------------------------------------- update auth.users
  const { data: updated, error: updateError } = await admin.auth.admin.updateUserById(
    userId,
    { email, email_confirm: true },
  );

  if (updateError || !updated?.user) {
    const code = (updateError as { code?: string } | null)?.code ?? "";
    if (
      code === "email_exists" ||
      /already|registered|exists|in use/i.test(updateError?.message ?? "")
    ) {
      return json({ error: "EMAIL_IN_USE" }, 409);
    }
    return json({ error: "EMAIL_UPDATE_FAILED", detail: updateError?.message ?? null }, 400);
  }

  // ------------------------------------------------- sincroniza bridge_profiles
  // Mantém o espelho de leitura (RLS) alinhado com o Auth. O campo é NOT NULL
  // UNIQUE; violação aqui significa corrida com outro cadastro -> 409 honesto.
  const { error: syncError } = await admin
    .from("bridge_profiles")
    .update({ email })
    .eq("id", userId);

  if (syncError) {
    if (/duplicate key|unique/i.test(syncError.message)) {
      return json({ error: "EMAIL_IN_USE" }, 409);
    }
    return json({ error: "PROFILE_SYNC_FAILED", detail: syncError.message }, 207);
  }

  return json({ user_id: userId, email, updated: true }, 200);
});
