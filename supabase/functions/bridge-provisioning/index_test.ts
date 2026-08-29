// ============================================================================
// Testes de bridge-provisioning
//
// Cobre:
//   - JWT válido CLIENT ACTIVE com config -> 200 configured=true (sem secrets)
//   - JWT válido CLIENT ACTIVE SEM config -> 200 configured=false
//   - JWT ausente/inválido                -> 401
//   - perfil inexistente                  -> 403
//   - usuário inativo (PENDING)           -> 403
//   - usuário suspenso (SUSPENDED)        -> 403
//   - DOMNEX_ADMIN (sem client_id)        -> 200 configured=false
//   - config desabilitada                 -> 200 configured=false
//   - config incompleta (token em branco) -> 200 configured=false
//   - resposta nunca contém nenhum dos tipos de segredo
//   - método não suportado                -> 405
//   - env obrigatória ausente             -> 500
//
// Executar (a partir de supabase/functions):
//   deno test --allow-env bridge-provisioning/index_test.ts
// ============================================================================

import {
  assertEquals,
} from "https://deno.land/std@0.224.0/assert/mod.ts";
import { handleRequest } from "./index.ts";

// ---------------------------------------------------------------------------
// Helpers de mock
// ---------------------------------------------------------------------------

type Profile = {
  role: string;
  status: string;
  client_id: string | null;
};

type ConfigRow = {
  target_system_name?: string;
  api_base_url?: string;
  bridge_token?: string;
  enabled?: boolean;
} | null;

type ClientRow = {
  status: string;
} | null;

interface FakeClientOpts {
  getUserError?: Error | null;
  user?: { id: string } | null;
  profile?: Profile | null;
  profileError?: Error | null;
  config?: ConfigRow;
  configError?: Error | null;
  client?: ClientRow;
  clientError?: Error | null;
}

function fakeChainOf<T>(resolve: () => Promise<{ data: T | null; error: any }>) {
  const chain = {
    select: () => chain,
    eq: () => chain,
    single: async () => resolve(),
    maybeSingle: async () => resolve(),
  };
  return chain;
}

function fakeSupabaseFactory(opts: FakeClientOpts) {
  // Identifica o chamador da query pelo texto do `select`; a primeira cliente
  // (anon + JWT) consulta bridge_profiles; a segunda (service_role) consulta
  // bridge_configs e bridge_clients.
  return (url: string, key: string) => {
    const isPrivileged = key === "service-role-key";
    const dbQuery = isPrivileged
      ? {
          select: (cols: string) => {
            if (cols.includes("target_system_name")) {
              return fakeChainOf(async () => ({
                data: opts.config ?? null,
                error: opts.configError ?? null,
              }));
            }
            return fakeChainOf(async () => ({
              data: opts.client ?? null,
              error: opts.clientError ?? null,
            }));
          },
        }
      : {
          select: () =>
            fakeChainOf(async () => {
              if (opts.profileError) return { data: null, error: opts.profileError };
              if (!opts.profile) return { data: null, error: { message: "not found" } };
              return { data: opts.profile, error: null };
            }),
        };

    const chain = {
      select: () => chain,
      eq: () => chain,
      single: async () => {
        if (isPrivileged) return { data: null, error: null };
        if (opts.profileError) return { data: null, error: opts.profileError };
        if (!opts.profile) return { data: null, error: { message: "not found" } };
        return { data: opts.profile, error: null };
      },
      maybeSingle: async () => {
        if (isPrivileged) return { data: null, error: null };
        return { data: null, error: null };
      },
    };

    return {
      auth: {
        getUser: async (_jwt: string) => {
          if (opts.getUserError) return { data: null, error: opts.getUserError };
          if (!opts.user) return { data: null, error: { message: "invalid token" } };
          return { data: { user: opts.user }, error: null };
        },
      },
      from: (_table: string) => isPrivileged ? dbQuery : chain,
    } as {
      auth: { getUser: (jwt: string) => Promise<{ data: any; error: any }> };
      from: (table: string) => any;
    };
  };
}

function buildRequest(
  method = "GET",
  {
    jwt = "TOKEN-CLIENT",
    url = "https://bknttvuiqsrkftodcsku.supabase.co/functions/v1/bridge-provisioning",
  }: { jwt?: string | null; url?: string } = {}
): Request {
  const headers = new Headers();
  if (jwt !== null) headers.set("Authorization", `Bearer ${jwt}`);
  return new Request(url, { method, headers });
}

function baseEnv(): Record<string, string | undefined> {
  return {
    SUPABASE_URL: "https://bknttvuiqsrkftodcsku.supabase.co",
    SUPABASE_ANON_KEY: "anon-public-key",
    SUPABASE_SERVICE_ROLE_KEY: "service-role-key",
  };
}

function clientProfile(clientId: string | null): Profile {
  return { role: "CLIENT", status: "ACTIVE", client_id: clientId };
}

function defaultConfig(): NonNullable<ConfigRow> {
  return {
    target_system_name: "CFI",
    api_base_url: "https://cfi.example.com/functions/v1/ingest",
    bridge_token: "tok-operacional-cliente",
    enabled: true,
  };
}

function defaultClient(): ClientRow {
  return { status: "ACTIVE" };
}

// ---------------------------------------------------------------------------
// CENÁRIO 1: CLIENT ACTIVE com config -> 200 configured=true (sem secrets)
// ---------------------------------------------------------------------------

Deno.test("CLIENT ACTIVE com config devolve 200 configured=true e não vaza secrets", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "client-user-id" },
      profile: clientProfile("client-uuid"),
      config: defaultConfig(),
      client: defaultClient(),
    }) as any,
  });

  assertEquals(result.status, 200);
  const body = result.body as Record<string, unknown>;
  assertEquals(body.configured, true);
  assertEquals(body.targetSystemName, "CFI");
  assertEquals(body.apiBaseUrl, "https://cfi.example.com/functions/v1/ingest");
  assertEquals(body.bridgeToken, "tok-operacional-cliente");

  const bodyText = JSON.stringify(body);
  assertEquals(bodyText.includes("service-role-key"), false);
  assertEquals(bodyText.includes("service_role"), false);
  assertEquals(bodyText.toLowerCase().includes("m2m"), false);
  assertEquals(bodyText.includes("SUPABASE_SERVICE_ROLE_KEY"), false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 2: CLIENT ACTIVE sem config -> 200 configured=false
// ---------------------------------------------------------------------------

Deno.test("CLIENT ACTIVE sem config devolve 200 configured=false", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "client-user-id" },
      profile: clientProfile("client-uuid"),
      config: null,
      client: defaultClient(),
    }) as any,
  });

  assertEquals(result.status, 200);
  assertEquals((result.body as Record<string, unknown>).configured, false);
  const bodyText = JSON.stringify(result.body);
  assertEquals(bodyText.includes("bridgeToken"), false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 3: JWT ausente -> 401
// ---------------------------------------------------------------------------

Deno.test("JWT ausente -> 401", async () => {
  const result = await handleRequest(buildRequest("GET", { jwt: null }), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({}) as any,
  });
  assertEquals(result.status, 401);
  assertEquals((result.body as { error: string }).error, "UNAUTHENTICATED");
});

// ---------------------------------------------------------------------------
// CENÁRIO 4: JWT inválido (getUser falha) -> 401
// ---------------------------------------------------------------------------

Deno.test("JWT inválido -> 401", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      getUserError: new Error("invalid token"),
    }) as any,
  });
  assertEquals(result.status, 401);
  assertEquals((result.body as { error: string }).error, "UNAUTHENTICATED");
});

// ---------------------------------------------------------------------------
// CENÁRIO 5: perfil inexistente -> 403
// ---------------------------------------------------------------------------

Deno.test("perfil inexistente -> 403 PROFILE_MISSING", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "ghost-id" },
      profile: null,
    }) as any,
  });
  assertEquals(result.status, 403);
  assertEquals((result.body as { error: string }).error, "PROFILE_MISSING");
});

// ---------------------------------------------------------------------------
// CENÁRIO 6: usuário inativo (PENDING) -> 403
// ---------------------------------------------------------------------------

Deno.test("usuário inativo (PENDING) -> 403", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "pending-id" },
      profile: { role: "CLIENT", status: "PENDING", client_id: null },
    }) as any,
  });
  assertEquals(result.status, 403);
  assertEquals((result.body as { error: string }).error, "FORBIDDEN");
});

// ---------------------------------------------------------------------------
// CENÁRIO 7: usuário suspenso (SUSPENDED) -> 403
// ---------------------------------------------------------------------------

Deno.test("usuário suspenso (SUSPENDED) -> 403", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "suspended-id" },
      profile: { role: "CLIENT", status: "SUSPENDED", client_id: "client-uuid" },
    }) as any,
  });
  assertEquals(result.status, 403);
  assertEquals((result.body as { error: string }).error, "FORBIDDEN");
});

// ---------------------------------------------------------------------------
// CENÁRIO 8: DOMNEX_ADMIN (sem client_id) -> 200 configured=false
// ---------------------------------------------------------------------------

Deno.test("DOMNEX_ADMIN sem client_id -> 200 configured=false", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "admin-id" },
      profile: { role: "DOMNEX_ADMIN", status: "ACTIVE", client_id: null },
      config: defaultConfig(),
      client: defaultClient(),
    }) as any,
  });
  assertEquals(result.status, 200);
  assertEquals((result.body as Record<string, unknown>).configured, false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 9: config desabilitada -> 200 configured=false
// ---------------------------------------------------------------------------

Deno.test("config desabilitada -> 200 configured=false", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "client-user-id" },
      profile: clientProfile("client-uuid"),
      config: { ...defaultConfig(), enabled: false },
      client: defaultClient(),
    }) as any,
  });
  assertEquals(result.status, 200);
  assertEquals((result.body as Record<string, unknown>).configured, false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 10: config incompleta (token em branco) -> 200 configured=false
// ---------------------------------------------------------------------------

Deno.test("config com token em branco -> 200 configured=false (sem token)", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "client-user-id" },
      profile: clientProfile("client-uuid"),
      config: { ...defaultConfig(), bridge_token: "   " },
      client: defaultClient(),
    }) as any,
  });
  assertEquals(result.status, 200);
  assertEquals((result.body as Record<string, unknown>).configured, false);
  const bodyText = JSON.stringify(result.body);
  assertEquals(bodyText.includes("bridgeToken"), false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 11: cliente (organização) não-ativo -> 200 configured=false
// ---------------------------------------------------------------------------

Deno.test("cliente não-ativo -> 200 configured=false", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "client-user-id" },
      profile: clientProfile("client-uuid"),
      config: defaultConfig(),
      client: { status: "SUSPENDED" },
    }) as any,
  });
  assertEquals(result.status, 200);
  assertEquals((result.body as Record<string, unknown>).configured, false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 12: método não suportado -> 405
// ---------------------------------------------------------------------------

Deno.test("método não suportado (POST) -> 405", async () => {
  const result = await handleRequest(buildRequest("POST"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({}) as any,
  });
  assertEquals(result.status, 405);
});

// ---------------------------------------------------------------------------
// CENÁRIO 13: env obrigatória ausente -> 500
// ---------------------------------------------------------------------------

Deno.test("SUPABASE_SERVICE_ROLE_KEY ausente -> 500 fail-closed", async () => {
  const env = baseEnv();
  delete env.SUPABASE_SERVICE_ROLE_KEY;
  const result = await handleRequest(buildRequest("GET"), {
    env,
    createSupabaseClient: fakeSupabaseFactory({}) as any,
  });
  assertEquals(result.status, 500);
  const bodyText = JSON.stringify(result.body);
  assertEquals(bodyText.includes("service-role-key"), false);
});

// ---------------------------------------------------------------------------
// CENÁRIO 14: falha ao ler a config -> 500 (nunca vira configured=false)
// ---------------------------------------------------------------------------

Deno.test("falha ao ler a config -> 500 (não mascarada como não-configurado)", async () => {
  const result = await handleRequest(buildRequest("GET"), {
    env: baseEnv(),
    createSupabaseClient: fakeSupabaseFactory({
      user: { id: "client-user-id" },
      profile: clientProfile("client-uuid"),
      client: defaultClient(),
      configError: new Error("db down"),
    }) as any,
  });
  assertEquals(result.status, 500);
  assertEquals((result.body as { error: string }).error, "CONFIG_LOOKUP_FAILED");
});