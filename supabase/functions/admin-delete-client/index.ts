// ============================================================================
// Edge Function: admin-delete-client
//
// Exclusão DEFINITIVA e irreversível de um cliente ("Zona de risco" em
// Administração → Acessos). Operação exclusiva de DOMNEX_ADMIN ACTIVE,
// revalidada AQUI no servidor — a interface nunca é a autoridade.
//
// O que é removido, nesta ordem:
//   1. Todos os usuários do Supabase Auth vinculados ao cliente
//      (auth.admin.deleteUser) — sem conta Auth, o login volta a falhar para
//      todos eles IMEDIATAMENTE. Os perfis em bridge_profiles caem sozinhos
//      pela FK ON DELETE CASCADE (bridge_profiles.id -> auth.users.id).
//   2. Somente se TODOS os usuários foram removidos e NENHUM perfil residual
//      permanecer apontando para o cliente: a linha em bridge_clients.
//
// Guardas (defesa em profundidade):
//   * Chamador precisa ser bridge_profile DOMNEX_ADMIN + ACTIVE;
//   * Cliente inexistente -> 404 CLIENT_NOT_FOUND;
//   * Auto-exclusão bloqueada (chamador vinculado ao próprio cliente)
//     -> 403 SELF_DELETE_FORBIDDEN;
//   * Qualquer perfil DOMNEX_ADMIN vinculado ao cliente (não deveria existir
//     pela constraint do banco) -> 409 ADMIN_LINKED_PROTECTED;
//   * Falha parcial na remoção dos usuários -> 207 PARTIAL_DELETE_FAILED com
//     lista detalhada; o cliente NÃO é excluído nesse caso (estado coerente).
//   * Perfis residuais após a remoção -> 207 RESIDUAL_PROFILES; o cliente
//     também NÃO é excluído.
//
// Segurança:
//   * service_role key vive APENAS aqui (env runtime das Edge Functions).
//   * RLS de bridge_profiles/bridge_clients permanece inalterada — esta
//     função usa a conexão de serviço, que contorna RLS por design.
//   * A confirmação por digitação do nome acontece na UI; aqui a operação é
//     identificada por client_id imutável (nunca por nome digitado).
//
// Deploy (requer CLI logado):
//   supabase functions deploy admin-delete-client --project-ref bknttvuiqsrkftodcsku
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface FailedUser {
  user_id: string;
  email: string | null;
  error: string;
}

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
  const callerId = userData.user.id;

  const { data: callerProfile, error: profileError } = await callerClient
    .from("bridge_profiles")
    .select("id, role, status")
    .eq("id", callerId)
    .single();

  if (profileError || !callerProfile) return json({ error: "PROFILE_MISSING" }, 403);
  if (callerProfile.role !== "DOMNEX_ADMIN" || callerProfile.status !== "ACTIVE") {
    return json({ error: "FORBIDDEN" }, 403);
  }

  // ----------------------------------------------------------------- payload
  let payload: { client_id?: string };
  try {
    payload = await req.json();
  } catch (_err) {
    return json({ error: "INVALID_JSON" }, 400);
  }
  const clientId = (payload.client_id ?? "").trim();
  if (clientId === "") return json({ error: "CLIENT_ID_REQUIRED" }, 400);

  const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // ------------------------------------------------------------ cliente real
  const { data: client, error: clientError } = await admin
    .from("bridge_clients")
    .select("id, name")
    .eq("id", clientId)
    .maybeSingle();

  if (clientError) {
    return json({ error: "CLIENT_LOOKUP_FAILED", detail: clientError.message }, 500);
  }
  if (!client) return json({ error: "CLIENT_NOT_FOUND" }, 404);

  // ------------------------------------------------------- perfis vinculados
  const { data: linkedProfiles, error: linkedError } = await admin
    .from("bridge_profiles")
    .select("id, email, role, status")
    .eq("client_id", clientId);

  if (linkedError) {
    return json({ error: "PROFILES_LOOKUP_FAILED", detail: linkedError.message }, 500);
  }

  const profiles = linkedProfiles ?? [];

  if (profiles.some((p) => p.id === callerId)) {
    return json(
      { error: "SELF_DELETE_FORBIDDEN", detail: "O administrador está vinculado ao próprio cliente." },
      403
    );
  }
  if (profiles.some((p) => p.role === "DOMNEX_ADMIN")) {
    return json(
      { error: "ADMIN_LINKED_PROTECTED", detail: "Existem acessos administrativos vinculados a este cliente." },
      409
    );
  }

  // ------------------------------------------- remoção REAL dos usuários Auth
  const failedUsers: FailedUser[] = [];
  let deletedUsers = 0;

  for (const profile of profiles) {
    try {
      const { error: deleteError } = await admin.auth.admin.deleteUser(profile.id);
      if (deleteError) {
        failedUsers.push({
          user_id: profile.id,
          email: profile.email ?? null,
          error: deleteError.message,
        });
      } else {
        deletedUsers += 1;
      }
    } catch (err) {
      failedUsers.push({
        user_id: profile.id,
        email: profile.email ?? null,
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  if (failedUsers.length > 0) {
    // Estado parcial: alguns usuários foram removidos, outros não.
    // NÃO tocamos na linha do cliente — ele continua existindo de forma
    // coerente, e o administrador pode tentar novamente.
    return json(
      {
        error: "PARTIAL_DELETE_FAILED",
        detail:
          "Alguns usuários não puderam ser removidos. O cliente NÃO foi excluído; tente novamente.",
        deleted_users: deletedUsers,
        remaining_users: failedUsers.length,
        failed_users: failedUsers,
      },
      207
    );
  }

  // ------------------------------------------------------- perfis residuais?
  const { data: residuals, error: residualError } = await admin
    .from("bridge_profiles")
    .select("id")
    .eq("client_id", clientId);

  if (residualError) {
    return json({ error: "RESIDUAL_CHECK_FAILED", detail: residualError.message }, 500);
  }
  if ((residuals ?? []).length > 0) {
    return json(
      {
        error: "RESIDUAL_PROFILES",
        detail: "Ainda existem perfis vinculados ao cliente. Ele NÃO foi excluído.",
        remaining_users: (residuals ?? []).length,
      },
      207
    );
  }

  // --------------------------------------------------- exclusão do cliente
  const { error: deleteClientError } = await admin
    .from("bridge_clients")
    .delete()
    .eq("id", clientId);

  if (deleteClientError) {
    return json({ error: "CLIENT_DELETE_FAILED", detail: deleteClientError.message }, 207);
  }

  return json(
    {
      client_id: clientId,
      client_name: client.name,
      deleted_users: deletedUsers,
    },
    200
  );
});
