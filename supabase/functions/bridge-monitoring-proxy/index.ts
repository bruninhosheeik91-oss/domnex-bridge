// ============================================================================
// Edge Function: bridge-monitoring-proxy
//
// Proxy seguro, do projeto DOMNEX BRIDGE, para o MONITORAMENTO DE BRIDGES
// hospedado no projeto CFI.
//
// Fluxo:
//   Android DOMNEX_ADMIN -> bridge-monitoring-proxy
//     -> valida JWT do Supabase do DOMNEX BRIDGE (auth.getUser)
//     -> confirma bridge_profiles.role  = DOMNEX_ADMIN
//     -> confirma bridge_profiles.status = ACTIVE
//     -> usa M2M_MONITORING_SECRET (env do runtime, NUNCA no APK)
//     -> chama server-to-server o endpoint CFI bridge-monitoring
//     -> devolve ao Android APENAS o JSON sanitizado do CFI
//
// Segurança:
//   * O chamador precisa ser um bridge_profile REAL com role DOMNEX_ADMIN e
//     status ACTIVE — revalidado AQUI no servidor, nunca só na interface.
//   * M2M_MONITORING_SECRET e a service_role do CFI vivem somente no ambiente
//     (env) das Edge Functions. Nunca entram no APK nem no repositório.
//   * O secret NUNCA é logado e NUNCA aparece na resposta ao Android.
//   * Não usa bridge_token de ingestão. CLIENT é rejeitado (403).
//
// Erros (status preservados — falha nunca é mascarada como sucesso):
//   401 -> JWT Bridge ausente/inválido
//   403 -> usuário não é DOMNEX_ADMIN ACTIVE / perfil inexistente
//   502 -> erro de rede / resposta inválida na chamada server-to-server
//   503 -> backend CFI indisponível (status 5xx do CFI) ou M2M/URL ausente
//   200 -> sucesso (JSON sanitizado do CFI repassado na íntegra)
//
// Env necessárias (configurar em Settings -> Edge Functions do DOMNEX BRIDGE;
// SUPABASE_URL e SUPABASE_ANON_KEY já são injetadas automaticamente):
//   M2M_MONITORING_SECRET -> secret M2M usado no Bearer para o CFI
//   CFI_MONITORING_URL    -> https://xfvdqbuwqzenxdvtiqqd.supabase.co/functions/v1/bridge-monitoring
//
// Se faltar M2M_MONITORING_SECRET ou CFI_MONITORING_URL, a função responde
// 503 (fail-closed), sem expor detalhes.
//
// Deploy (requer CLI logado):
//   supabase functions deploy bridge-monitoring-proxy --project-ref bknttvuiqsrkftodcsku
// ============================================================================

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ---------------------------------------------------------------------------
// Interfaces/injeção de dependências (testável)
// ---------------------------------------------------------------------------

export interface Deps {
  env: Record<string, string | undefined>;
  createSupabaseClient?: (url: string, key: string, options?: unknown) => any;
  doFetch?: typeof fetch;
}

export interface HandlerResult {
  status: number;
  body?: unknown;
  headers?: Record<string, string>;
  rawBody?: string;
}

export type RawBody = string | null;

function isJsonContentType(res: Response): boolean {
  return (res.headers.get("content-type") ?? "").includes("application/json");
}

export function errorJson(error: string, status: number): HandlerResult {
  return { status, body: { error } };
}

// ---------------------------------------------------------------------------
// Núcleo da função (exportado para testes unitários)
// ---------------------------------------------------------------------------

export async function handleRequest(
  req: Request,
  deps: Deps = { env: Deno.env.toObject() }
): Promise<HandlerResult> {
  const method = req.method;

  // Somente leitura/monitoramento. GET (query params) e POST (com body) são
  // aceitos e repassados ao CFI conforme recebidos.
  if (method !== "GET" && method !== "POST") {
    return { status: 405, body: { error: "METHOD_NOT_ALLOWED" } };
  }

  const SUPABASE_URL = deps.env["SUPABASE_URL"];
  const ANON_KEY = deps.env["SUPABASE_ANON_KEY"];
  const M2M_SECRET = deps.env["M2M_MONITORING_SECRET"];
  const CFI_URL = deps.env["CFI_MONITORING_URL"];

  if (!SUPABASE_URL || !ANON_KEY) {
    return errorJson("SERVER_MISCONFIGURED", 500);
  }

  // ------------------------------------------------------------------ caller
  const authHeader = req.headers.get("Authorization") ?? "";
  const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!jwt) return errorJson("UNAUTHENTICATED", 401);

  const createSupabaseClient: (url: string, key: string, options?: unknown) => any =
    deps.createSupabaseClient ??
    ((url: string, key: string, options?: unknown) =>
      createClient(url, key, options as Parameters<typeof createClient>[2]));

  const callerClient = createSupabaseClient(SUPABASE_URL, ANON_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });

  const { data: userData, error: userError } = await callerClient.auth.getUser(jwt);
  if (userError || !userData?.user) return errorJson("UNAUTHENTICATED", 401);

  const { data: callerProfile, error: profileError } = await callerClient
    .from("bridge_profiles")
    .select("id, role, status")
    .eq("id", userData.user.id)
    .single();

  if (profileError || !callerProfile) return errorJson("PROFILE_MISSING", 403);
  if (callerProfile.role !== "DOMNEX_ADMIN" || callerProfile.status !== "ACTIVE") {
    return errorJson("FORBIDDEN", 403);
  }

  // ------------------------------------------------------- fail-closed secrets
  if (!M2M_SECRET || !CFI_URL) {
    // Sem detalhes: não revela qual env falta nem qualquer segredo.
    return errorJson("UPSTREAM_UNCONFIGURED", 503);
  }

  // ------------------------------------------- chamada server-to-server (CFI)
  // Reenviamos method + query string + body do chamador, além de payload
  // customizado. Nunca repassamos/buscamos segredos aqui além do M2M secret.
  const targetUrl = new URL(CFI_URL);
  const incomingUrl = new URL(req.url);
  incomingUrl.searchParams.forEach((v, k) => {
    if (!targetUrl.searchParams.has(k)) targetUrl.searchParams.set(k, v);
  });

  let bodyToSend: BodyInit | undefined;
  if (method === "POST") {
    bodyToSend = await req.text();
  }

  const doFetch = deps.doFetch ?? fetch;

  let upstream: Response;
  try {
    upstream = await doFetch(targetUrl.toString(), {
      method,
      headers: {
        Authorization: `Bearer ${M2M_SECRET}`,
        ...(method === "POST" ? { "Content-Type": "application/json" } : {}),
      },
      body: bodyToSend,
    });
  } catch (_err) {
    // Falha de rede / conectividade com o CFI. Sem detalhes internos.
    return errorJson("UPSTREAM_UNREACHABLE", 502);
  }

  const upstreamStatus = upstream.status;
  const isJson = isJsonContentType(upstream);
  const upstreamText = await upstream.text();

  if (upstreamStatus >= 500) {
    // Backend CFI indisponível — falha real, não mascaramos como sucesso.
    return errorJson("UPSTREAM_ERROR", 503);
  }

  // 4xx do CFI (ex.: o M2M foi rejeitado lá): preservamos a semântica.
  if (upstreamStatus >= 400 && upstreamStatus < 500) {
    return errorJson("UPSTREAM_REJECTED", upstreamStatus);
  }

  if (!isJson) {
    return errorJson("UPSTREAM_INVALID", 502);
  }

  return {
    status: 200,
    rawBody: upstreamText,
    headers: { "Content-Type": "application/json" },
  };
}

// ---------------------------------------------------------------------------
// Respostas auxiliares
// ---------------------------------------------------------------------------

function toResponse(result: HandlerResult): Response {
  if (result.rawBody !== undefined) {
    return new Response(result.rawBody, {
      status: result.status,
      headers: result.headers ?? { "Content-Type": "application/json" },
    });
  }
  return new Response(JSON.stringify(result.body), {
    status: result.status,
    headers: { "Content-Type": "application/json" },
  });
}

if (import.meta.main) {
  Deno.serve(async (req: Request) => {
    const result = await handleRequest(req, { env: Deno.env.toObject() });
    return toResponse(result);
  });
}
