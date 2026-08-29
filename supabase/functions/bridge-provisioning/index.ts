// ============================================================================
// Edge Function: bridge-provisioning
//
// Reprovisionamento automático do DOMNEX BRIDGE após reinstalação:
//   Android (CLIENT autenticado) -> bridge-provisioning
//     -> valida JWT do Supabase do DOMNEX BRIDGE (auth.getUser)
//     -> confirma bridge_profiles.status = ACTIVE
//     -> identifica o cliente (bridge_profiles.client_id -> bridge_clients)
//     -> retorna APENAS a configuração operacional daquele cliente
//        (targetSystemName, apiBaseUrl, bridgeToken) — nada administrativo.
//
// Resposta esperada:
//   200 { "configured": true, "targetSystemName": "...", "apiBaseUrl": "...",
//         "bridgeToken": "..." }
//   200 { "configured": false }   -> usuário sem Bridge configurado
//
// Segurança:
//   * Chamador precisa ser um bridge_profile REAL com status ACTIVE — revalidado
//     AQUI no servidor, nunca só na interface. Perfil PENDING/SUSPENDED e
//     usuário inexistente são rejeitados.
//   * DOMNEX_ADMIN não possui client_id: a função devolve configured=false
//     (o fluxo de reprovisionamento é de CLIENT; o admin nunca depende dele).
//   * A leitura da configuração é feita com SUPABASE_SERVICE_ROLE_KEY DENTRO
//     da função (env do backend, NUNCA no APK) porque bridge_configs tem RLS
//     FORCE sem policies — o token operacional não é legível via REST nem por
//     outro usuário autenticado.
//   * NUNCA retorna service_role, M2M_MONITORING_SECRET, senha administrativa
//     ou segredo de servidor. Apenas o TOKEN OPERACIONAL DE INGESTÃO do
//     próprio cliente (o mesmo que o SaleSender já usava localmente).
//   * O bridgeToken NUNCA é logado (sanitização por tags de presença/status).
//
// Erros:
//   401 -> JWT ausente/inválido (auth.getUser falhou)
//   403 -> perfil inexistente (PROFILE_MISSING) ou status != ACTIVE (FORBIDDEN)
//   405 -> método não suportado
//   500 -> env obrigatória ausente (SERVER_MISCONFIGURED)
//
// Env necessárias (SUPABASE_URL, SUPABASE_ANON_KEY e SUPABASE_SERVICE_ROLE_KEY
// são injetadas automaticamente no runtime pelo Supabase):
//   SUPABASE_URL               -> automática
//   SUPABASE_ANON_KEY          -> automática
//   SUPABASE_SERVICE_ROLE_KEY  -> automática
//
// verify_jwt = false (config.toml) — INTENCIONAL e seguro:
//   O projeto DOMNEX BRIDGE emite tokens com as NOVAS chaves assimétricas
//   (RS256) que o check legado do gateway (verify_jwt = true) não reconhece —
//   devolvendo 401 antes do código. Com o check do gateway desligado, a
//   autenticação é feita 100% AQUI (fail-closed): sem JWT -> 401, JWT inválido
//   (auth.getUser) -> 401, perfil inexistente -> 403, status != ACTIVE -> 403.
//
// Deploy (requer CLI logado):
//   supabase functions deploy bridge-provisioning \
//     --project-ref bknttvuiqsrkftodcsku --no-verify-jwt
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ---------------------------------------------------------------------------
// Interfaces/injeção de dependências (testável)
// ---------------------------------------------------------------------------

export interface Deps {
  env: Record<string, string | undefined>;
  createSupabaseClient?: (url: string, key: string, options?: unknown) => any;
}

export interface HandlerResult {
  status: number;
  body?: unknown;
  headers?: Record<string, string>;
}

export function errorJson(error: string, status: number): HandlerResult {
  return { status, body: { error } };
}

// ---------------------------------------------------------------------------
// Log sanitizado por etapas. NUNCA imprime valores — apenas presença/ausência
// e códigos de status. Em nenhum ponto o bridgeToken, JWT, apikey ou qualquer
// segredo aparece nos logs.
// ---------------------------------------------------------------------------

const AUTH_TAG = "[PROVISIONING_AUTH]";
const SUCCESS_TAG = "[PROVISIONING_SUCCESS]";
const NOT_CONFIGURED_TAG = "[PROVISIONING_NOT_CONFIGURED]";
const ERROR_TAG = "[PROVISIONING_ERROR]";

function provisioningLog(tag: string, message: string): void {
  // eslint-disable-next-line no-console
  console.log(`${tag} ${message}`);
}

/** Presença/ausência apenas — nunca o valor real. */
function headerPresence(req: Request, name: string): string {
  return req.headers.get(name) ? "present" : "missing";
}

/** Código sanitizado do SDK (status/code) — sem mensagem crua (não loga token). */
function sanitizeSdkError(err: unknown): string {
  const e = err as { status?: number; code?: string } | null;
  const status = e?.status ?? "n/a";
  const code = e?.code ?? "n/a";
  return `status=${status} code=${code}`;
}

// ---------------------------------------------------------------------------
// Núcleo da função (exportado para testes unitários)
// ---------------------------------------------------------------------------

export async function handleRequest(
  req: Request,
  deps: Deps = { env: Deno.env.toObject() }
): Promise<HandlerResult> {
  const method = req.method;
  provisioningLog(AUTH_TAG, `request_received method=${method}`);

  if (method !== "GET") {
    provisioningLog(AUTH_TAG, "method_not_allowed");
    return errorJson("METHOD_NOT_ALLOWED", 405);
  }

  const SUPABASE_URL = deps.env["SUPABASE_URL"];
  const ANON_KEY = deps.env["SUPABASE_ANON_KEY"];
  const SERVICE_ROLE_KEY = deps.env["SUPABASE_SERVICE_ROLE_KEY"];

  if (!SUPABASE_URL || !ANON_KEY || !SERVICE_ROLE_KEY) {
    provisioningLog(
      ERROR_TAG,
      "server_misconfigured url=" +
        `${SUPABASE_URL ? "present" : "missing"} anon=` +
        `${ANON_KEY ? "present" : "missing"} service_role=` +
        `${SERVICE_ROLE_KEY ? "present" : "missing"}`
    );
    return errorJson("SERVER_MISCONFIGURED", 500);
  }

  // ------------------------------------------------------------------ caller
  // Presença/ausência apenas. Nunca imprime o JWT nem o header Authorization.
  provisioningLog(
    AUTH_TAG,
    `authorization_header=${headerPresence(req, "Authorization")} ` +
      `apikey_header=${headerPresence(req, "apikey")}`
  );

  const authHeader = req.headers.get("Authorization") ?? "";
  const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!jwt) {
    provisioningLog(AUTH_TAG, "authorization_missing");
    return errorJson("UNAUTHENTICATED", 401);
  }
  provisioningLog(AUTH_TAG, "bearer_present");

  const createSupabaseClient: (url: string, key: string, options?: unknown) => any =
    deps.createSupabaseClient ??
    ((url: string, key: string, options?: unknown) =>
      createClient(url, key, options as Parameters<typeof createClient>[2]));

  const callerClient = createSupabaseClient(SUPABASE_URL, ANON_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });

  const { data: userData, error: userError } = await callerClient.auth.getUser(jwt);
  if (userError || !userData?.user) {
    // Somente código sanitizado do SDK. Nunca a mensagem crua.
    provisioningLog(
      AUTH_TAG,
      `auth_get_user_failed ${sanitizeSdkError(userError)} ` +
        `user_data=${userData ? "present" : "missing"}`
    );
    return errorJson("UNAUTHENTICATED", 401);
  }
  provisioningLog(AUTH_TAG, "auth_get_user_success");

  const { data: callerProfile, error: profileError } = await callerClient
    .from("bridge_profiles")
    .select("role, status, client_id")
    .eq("id", userData.user.id)
    .single();

  if (profileError || !callerProfile) {
    provisioningLog(
      AUTH_TAG,
      `profile_missing error=${profileError ? "sim" : "nao"}`
    );
    return errorJson("PROFILE_MISSING", 403);
  }
  provisioningLog(
    AUTH_TAG,
    `profile_found role=${callerProfile.role} status=${callerProfile.status}`
  );

  // Somente usuário ACTIVE tem direito ao reprovisionamento.
  // PENDING/SUSPENDED/missing -> negado.
  if (callerProfile.status !== "ACTIVE") {
    provisioningLog(AUTH_TAG, "profile_forbidden_status");
    return errorJson("FORBIDDEN", 403);
  }

  // DOMNEX_ADMIN não possui client_id: não há Bridge próprio para reprovisionar.
  if (callerProfile.role !== "CLIENT" || !callerProfile.client_id) {
    provisioningLog(NOT_CONFIGURED_TAG, "no_client_associated");
    return { status: 200, body: { configured: false } };
  }

  // -------------------------------------------- leitura PRIVILEGIADA da config
  // bridge_configs tem RLS FORCE sem policies: nem o próprio CLIENT lê via
  // REST. Só aqui, após validar o chamador, a service_role (env do backend)
  // lê a configuração operacional daquele cliente.
  const privileged = createSupabaseClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const clientId = callerProfile.client_id as string;

  const [configQuery, clientQuery] = await Promise.all([
    privileged
      .from("bridge_configs")
      .select("target_system_name, api_base_url, bridge_token, enabled")
      .eq("client_id", clientId)
      .maybeSingle(),
    privileged
      .from("bridge_clients")
      .select("status")
      .eq("id", clientId)
      .maybeSingle(),
  ]);

  if (configQuery.error || clientQuery.error || !clientQuery.data) {
    provisioningLog(
      ERROR_TAG,
      `config_lookup_failed config_error=${configQuery.error ? "sim" : "nao"} ` +
        `client_error=${clientQuery.error ? "sim" : "nao"}`
    );
    return errorJson("CONFIG_LOOKUP_FAILED", 500);
  }

  const config = configQuery.data;
  const client = clientQuery.data;

  // Cliente suspenso ou config desabilitada/incompleta -> trata como não configurado.
  // Nunca devolve token nesse caso.
  if (
    !config ||
    config.enabled !== true ||
    client.status !== "ACTIVE" ||
    !isNonBlank(config.api_base_url) ||
    !isNonBlank(config.bridge_token)
  ) {
    provisioningLog(NOT_CONFIGURED_TAG, "no_config_available");
    return { status: 200, body: { configured: false } };
  }

  provisioningLog(
    SUCCESS_TAG,
    `configured=true client_id_present system_name_len=` +
      `${(config.target_system_name ?? "").length}`
  );

  return {
    status: 200,
    body: {
      configured: true,
      targetSystemName: config.target_system_name ?? "",
      apiBaseUrl: config.api_base_url,
      bridgeToken: config.bridge_token,
    },
  };
}

function isNonBlank(value: unknown): boolean {
  return typeof value === "string" && value.trim().length > 0;
}

// ---------------------------------------------------------------------------
// Ponto de entrada (servidor)
// ---------------------------------------------------------------------------

function toResponse(result: HandlerResult): Response {
  return new Response(JSON.stringify(result.body), {
    status: result.status,
    headers: result.headers ?? { "Content-Type": "application/json" },
  });
}

if (import.meta.main) {
  Deno.serve(async (req: Request) => {
    const result = await handleRequest(req, { env: Deno.env.toObject() });
    return toResponse(result);
  });
}