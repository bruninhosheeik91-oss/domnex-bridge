// ============================================================================
// Testes de bridge-monitoring-proxy
//
// Cobre:
//   - JWT válido DOMNEX_ADMIN ACTIVE -> 200
//   - JWT ausente/inválido           -> 401
//   - CLIENT                         -> 403
//   - DOMNEX_ADMIN SUSPENDED         -> 403
//   - perfil inexistente             -> 403
//   - M2M secret ausente             -> 503
//   - resposta 200 do CFI repassada  -> 200
//   - erro 4xx/5xx do CFI tratado    -> 401/403/503
//   - resposta sem secrets           -> sem token/secret/saltos
//   - secret nunca vaza em resposta  -> assert
//
// Executar (a partir de supabase/functions):
//   deno test --allow-env bridge-monitoring-proxy/index_test.ts
// ============================================================================

import {
  assertEquals,
} from "https://deno.land/std@0.224.0/assert/mod.ts";
import { handleRequest } from "./index.ts";

// ---------------------------------------------------------------------------
// Helpers de mock
// ---------------------------------------------------------------------------

type Profile = {
  id: string;
  role: string;
  status: string;
};

interface FakeClientOpts {
  getUserError?: Error | null;
  user?: { id: string } | null;
  profile?: Profile | null;
  profileError?: Error | null;
}

function fakeSupabaseFactory(opts: FakeClientOpts) {
  return () => {
    const dbQuery = (() => {
      const chain = {
        select: () => chain,
        eq: () => chain,
        single: async () => {
          if (opts.profileError) return { data: null, error: opts.profileError };
          if (!opts.profile) return { data: null, error: { message: "not found" } };
          return { data: opts.profile, error: null };
        },
      };
      return chain;
    })();

    return {
      auth: {
        getUser: async (_jwt: string) => {
          if (opts.getUserError) return { data: null, error: opts.getUserError };
          if (!opts.user) return { data: null, error: { message: "invalid token" } };
          return { data: { user: opts.user }, error: null };
        },
      },
      from: (_table: string) => dbQuery,
    } as {
      auth: { getUser: (jwt: string) => Promise<{ data: any; error: any }> };
      from: (table: string) => any;
    };
  };
}

function buildRequest(
  method = "GET",
  {
    jwt = "TOKEN-DOMNEX-ADMIN",
    url = "https://bknttvuiqsrkftodcsku.supabase.co/functions/v1/bridge-monitoring-proxy",
    body,
  }: { jwt?: string | null; url?: string; body?: string } = {}
): Request {
  const headers = new Headers();
  if (jwt !== null) headers.set("Authorization", `Bearer ${jwt}`);
  if (body !== undefined) headers.set("Content-Type", "application/json");
  return new Request(url, { method, headers, body });
}

function baseEnv(): Record<string, string | undefined> {
  return {
    SUPABASE_URL: "https://bknttvuiqsrkftodcsku.supabase.co",
    SUPABASE_ANON_KEY: "anon-public-key",
    M2M_MONITORING_SECRET: "m2m-super-secret",
    CFI_MONITORING_URL:
      "https://xfvdqbuwqzenxdvtiqqd.supabase.co/functions/v1/bridge-monitoring",
  };
}

type FetchHandler = (
  input: RequestInfo | URL,
  init?: RequestInit
) => Promise<Response>;

function fakeFetch(handler: FetchHandler) {
  return (input: RequestInfo | URL, init?: RequestInit) => handler(input, init);
}

function okUpstreamBody(): string {
  return JSON.stringify({
    message: "ok",
    bridges: [{ name: "Bridge-01", status: "RUNNING" }],
  });
}

// ---------------------------------------------------------------------------
// CENÁRIO 1: JWT válido DOMNEX_ADMIN ACTIVE -> 200, sem vazar secrets
// ---------------------------------------------------------------------------

Deno.test("JWT válido DOMNEX_ADMIN ACTIVE repassa 200 do CFI e não vaza secrets", async () => {
  const capturedAuth: string[] = [];
  const doFetch = fakeFetch(async (_input, init) => {
    const headers = new Headers((init?.headers ?? {}) as HeadersInit);
    const auth = headers.get("Authorization");
    if (auth) capturedAuth.push(auth);
    return new Response(okUpstreamBody(), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  });

  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "user-admin-id" },
      profile: { id: "user-admin-id", role: "DOMNEX_ADMIN", status: "ACTIVE" },
    }) as any,
    doFetch,
  });

  assertEquals(result.status, 200);
  assertEquals(result.rawBody, okUpstreamBody());
  // O secret M2M vai para o CFI, mas NUNCA na resposta ao Android.
  assertEquals(capturedAuth, ["Bearer m2m-super-secret"]);
  const bodyText = result.rawBody ?? JSON.stringify(result.body);
  assertEquals(bodyText.includes("m2m-super-secret"), false);
  assertEquals(bodyText.includes("service_role"), false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 2: JWT ausente -> 401
// ---------------------------------------------------------------------------

Deno.test("JWT ausente -> 401", async () => {
  const result = await handleRequest(buildRequest("GET", { jwt: null }), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({}) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 401);
  assertEquals((result.body as { error: string }).error, "UNAUTHENTICATED");
});

// ---------------------------------------------------------------------------
// CENÁRIO 3: JWT inválido (getUser falha) -> 401
// ---------------------------------------------------------------------------

Deno.test("JWT inválido -> 401", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      getUserError: new Error("invalid token"),
    }) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 401);
  assertEquals((result.body as { error: string }).error, "UNAUTHENTICATED");
});

// ---------------------------------------------------------------------------
// CENÁRIO 4: CLIENT -> 403
// ---------------------------------------------------------------------------

Deno.test("CLIENT é bloqueado -> 403", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "client-id" },
      profile: { id: "client-id", role: "CLIENT", status: "ACTIVE" },
    }) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 403);
  assertEquals((result.body as { error: string }).error, "FORBIDDEN");
});

// ---------------------------------------------------------------------------
// CENÁRIO 5: DOMNEX_ADMIN SUSPENDED -> 403
// ---------------------------------------------------------------------------

Deno.test("DOMNEX_ADMIN SUSPENDED é bloqueado -> 403", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "suspended-admin" },
      profile: { id: "suspended-admin", role: "DOMNEX_ADMIN", status: "SUSPENDED" },
    }) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 403);
  assertEquals((result.body as { error: string }).error, "FORBIDDEN");
});

// ---------------------------------------------------------------------------
// CENÁRIO 6: perfil inexistente -> 403
// ---------------------------------------------------------------------------

Deno.test("perfil inexistente -> 403", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "ghost-id" },
      profile: null,
    }) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 403);
  assertEquals((result.body as { error: string }).error, "PROFILE_MISSING");
});

// ---------------------------------------------------------------------------
// CENÁRIO 7: M2M secret ausente -> 503 (fail-closed, sem detalhes/segredo)
// ---------------------------------------------------------------------------

Deno.test("M2M secret ausente -> 503 fail-closed", async () => {
  const env = baseEnv();
  delete env.M2M_MONITORING_SECRET;

  const result = await handleRequest(buildRequest("GET"), {
    env,
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "admin" },
      profile: { id: "admin", role: "DOMNEX_ADMIN", status: "ACTIVE" },
    }) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 503);
  const bodyText = JSON.stringify(result.body);
  assertEquals(bodyText.includes("M2M"), false);
  assertEquals(bodyText.toLowerCase().includes("secret"), false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 8: CFI sem URL configurada -> 503
// ---------------------------------------------------------------------------

Deno.test("CFI_MONITORING_URL ausente -> 503 fail-closed", async () => {
  const env = baseEnv();
  delete env.CFI_MONITORING_URL;

  const result = await handleRequest(buildRequest("GET"), {
    env,
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "admin" },
      profile: { id: "admin", role: "DOMNEX_ADMIN", status: "ACTIVE" },
    }) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 503);
});

// ---------------------------------------------------------------------------
// CENÁRIO 9: erro de rede (fetch lança) -> 502
// ---------------------------------------------------------------------------

Deno.test("falha de rede na chamada ao CFI -> 502", async () => {
  const doFetch = fakeFetch(async () => {
    throw new Error("network down");
  });
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "admin" },
      profile: { id: "admin", role: "DOMNEX_ADMIN", status: "ACTIVE" },
    }) as any,
    doFetch,
  });
  assertEquals(result.status, 502);
  assertEquals((result.body as { error: string }).error, "UPSTREAM_UNREACHABLE");
});

// ---------------------------------------------------------------------------
// CENÁRIO 10: CFI responde 5xx -> 503 (não vira sucesso)
// ---------------------------------------------------------------------------

Deno.test("CFI responde 500 -> 503 (não mascarado como sucesso)", async () => {
  const doFetch = fakeFetch(async () =>
    new Response('{"error":"boom"}', { status: 500, headers: { "Content-Type": "application/json" } })
  );
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "admin" },
      profile: { id: "admin", role: "DOMNEX_ADMIN", status: "ACTIVE" },
    }) as any,
    doFetch,
  });
  assertEquals(result.status, 503);
  assertEquals((result.body as { error: string }).error, "UPSTREAM_ERROR");
});

// ---------------------------------------------------------------------------
// CENÁRIO 11: CFI responde 401/403 -> preserva status
// ---------------------------------------------------------------------------

for (const upstreamStatus of [401, 403]) {
  Deno.test(`CFI responde ${upstreamStatus} -> preserva status ${upstreamStatus}`, async () => {
    const doFetch = fakeFetch(async () =>
      new Response('{"error":"denied"}', { status: upstreamStatus, headers: { "Content-Type": "application/json" } })
    );
    const result = await handleRequest(buildRequest("GET"), {
      env: baseEnv(),
      createSupabaseClient: fakeSupabaseFactory({
        user: { id: "admin" },
        profile: { id: "admin", role: "DOMNEX_ADMIN", status: "ACTIVE" },
      }) as any,
      doFetch,
    });
    assertEquals(result.status, upstreamStatus);
    assertEquals((result.body as { error: string }).error, "UPSTREAM_REJECTED");
  });
}

// ---------------------------------------------------------------------------
// CENÁRIO 12: CFI responde conteúdo não-JSON -> 502
// ---------------------------------------------------------------------------

Deno.test("CFI responde não-JSON -> 502", async () => {
  const doFetch = fakeFetch(async () =>
    new Response("html or plain text", { status: 200, headers: { "Content-Type": "text/html" } })
  );
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "admin" },
      profile: { id: "admin", role: "DOMNEX_ADMIN", status: "ACTIVE" },
    }) as any,
    doFetch,
  });
  assertEquals(result.status, 502);
  assertEquals((result.body as { error: string }).error, "UPSTREAM_INVALID");
});

// ---------------------------------------------------------------------------
// CENÁRIO 13: método não suportado -> 405
// ---------------------------------------------------------------------------

Deno.test("método não suportado (DELETE) -> 405", async () => {
  const result = await handleRequest(buildRequest("DELETE"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({}) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 405);
});

// ---------------------------------------------------------------------------
// CENÁRIO 14: query string do chamador é repassada ao CFI
// ---------------------------------------------------------------------------

Deno.test("query params do chamador são repassados ao CFI", async () => {
  const capturedUrl: string[] = [];
  const doFetch = fakeFetch(async (input) => {
    capturedUrl.push(String(input));
    return new Response(okUpstreamBody(), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  });
  const result = await handleRequest(
    buildRequest("GET", {
      url:
        "https://bknttvuiqsrkftodcsku.supabase.co/functions/v1/bridge-monitoring-proxy?bridge_id=abc&from=2026-01-01",
    }),
    {
      env: baseEnv(),
      createSupabaseClient: fakeSupabaseFactory({
        user: { id: "admin" },
        profile: { id: "admin", role: "DOMNEX_ADMIN", status: "ACTIVE" },
      }) as any,
      doFetch,
    }
  );
  assertEquals(result.status, 200);
  assertEquals(capturedUrl.length, 1);
  assertEquals(capturedUrl[0].includes("bridge_id=abc"), true);
  assertEquals(capturedUrl[0].includes("from=2026-01-01"), true);
});

// ---------------------------------------------------------------------------
// CENÁRIO 15: env faltando -> 500
// ---------------------------------------------------------------------------

Deno.test("SUPABASE_URL ausente -> 500", async () => {
  const env = baseEnv();
  delete env.SUPABASE_URL;
  const result = await handleRequest(buildRequest("GET"), {
    env,
    createSupabaseClient: fakeSupabaseFactory({}) as any,
    doFetch: fakeFetch(async () => new Response("", { status: 500 })),
  });
  assertEquals(result.status, 500);
});
