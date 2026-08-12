# Shoe Store Support ChatBot — MCP Gateway RBAC Sample

A **AWS Bedrock + Streamlit** chatbot that logs users in with **[Auth0](https://auth0.com)**
and connects to the **NetApp Instaclustr MCP Gateway** over Streamable HTTP,
attaching the resulting access token as a Bearer token on every request. The
Gateway enforces **role-based tool access** via its per-persona **Access
Control Lists (ACLs)** — this app never decides what a user is allowed to do,
it just surfaces whatever the Gateway allows or denies.

This is the companion code for the follow-up tutorial *"MCP Gateway Tutorial:
Role-Based Access Control with a Cassandra Backend,"* building on
[shoe-store-support-chatbot](https://github.com/instaclustr/code-samples/tree/main/MCP-Gateway/shoe-store-support-chatbot).

## What it demonstrates

The agent connects to the same `supportchatbot` virtual server as the earlier
sample, now with three roles layered on top via Auth0 and the Gateway's
Access Control Lists, and a third backend type:

- **`support-agent`** — product lookups (`get_product_details`), order
  lookups (`get_orders`), and ticket filing (`submit_request`).
- **`merchandiser`** — product and supplier cost/margin lookups
  (`get_product_details`, `get_supplier_cost`) only.
- **`readonly-auditor`** — full read visibility across products, orders, and
  cost data, with no ability to file tickets.

```
Human (support-agent / merchandiser / readonly-auditor)
   │  logs in via Auth0 → access token carries a role claim
   ▼
Streamlit shoe-bot  ──Bearer token──▶  MCP Gateway "supportchatbot" Virtual Server
                                          ├── ordersapi      (HTTP)      → get_orders
                                          ├── supporttickets (Kafka)      → submit_request
                                          └── catalogdata    (Cassandra)  → get_product_details
                                                                          → get_supplier_cost
```

## Prerequisites

- The **MCP Gateway** setup from
  [shoe-store-support-chatbot](https://github.com/instaclustr/code-samples/tree/main/MCP-Gateway/shoe-store-support-chatbot) — the
  `supportchatbot` virtual server with `ordersapi` (HTTP Server) and
  `supporttickets` (Kafka) backends already attached.
- An **[Auth0](https://auth0.com) tenant** you control — [sign up free](https://auth0.com/signup)
  if you don't have one.
- An **AWS account** with **Amazon Bedrock** model access enabled in
  `us-east-1`.
- **Python 3.11+** and the [uv](https://docs.astral.sh/uv/) package manager.

## MCP Gateway setup

Before running the chatbot, add a Cassandra backend and configure Auth0 + RBAC
on the existing `supportchatbot` virtual server, in the
[Instaclustr console](https://console.instaclustr.com).

**1. Provision a Cassandra cluster**
Create a new Cassandra cluster from the Instaclustr console, then create the
`catalog` keyspace/tables and a least-privilege `mcpgateway_catalog` role
scoped to read-only access on them. See the tutorial for the full CQL.

**2. Add the Cassandra backend**
Create a backend named `catalogdata` (type: Cassandra), using your cluster's
Data Center ID and the `mcpgateway_catalog` credentials. Add two tools:
- `get_product_details` — looks up a product's name, description, category,
  list price, and stock status by `productid`.
- `get_supplier_cost` — looks up internal supplier cost/margin data by
  `productid`.

**3. Configure Auth0**
[Log in to Auth0](https://manage.auth0.com/) and create a Regular Web
Application, a Post-Login Action that adds a roles claim when the
`mcp_roles` scope is requested, three Roles (`support-agent`, `merchandiser`,
`readonly-auditor`), and one test user per role. See the
[Auth0 example configuration](https://www.instaclustr.com/support/documentation/mcp-gateway/mcp-gateway-identity-providers/mcp-gateway-auth0-example-configuration/)
doc for the full walkthrough.

**4. Enable OAuth on the virtual server**
On `supportchatbot`'s OAuth configuration section, set the Issuer, JWKS URI,
Audience, Scopes Supported (`mcp_roles`), and Roles Claim Name
(`https://mcp_gateway/user_roles`) from your Auth0 tenant. See
[Configure MCP Tool Access](https://www.instaclustr.com/support/documentation/mcp-gateway/using-mcp-gateway/configure-mcp-tool-access/).

**5. Create the three Access Control Lists**
- `support-agent` → `get_orders` (ordersapi), `submit_request`
  (supporttickets), `get_product_details` (catalogdata)
- `readonly-auditor` → `get_orders` (ordersapi), `get_product_details`
  (catalogdata), `get_supplier_cost` (catalogdata)
- `merchandiser` → `get_product_details` (catalogdata), `get_supplier_cost`
  (catalogdata)

**6. Verify**
The `supportchatbot` virtual server should now list three backends —
`ordersapi` (1 tool), `supporttickets` (1 tool), `catalogdata` (2 tools) —
and three Allowlist ACLs matching the tool counts above.

## Configuration

This sample reads its settings from a local `config.json`. Start from the
provided template:

```bash
cp config.example.json config.json
```

Fill in your Auth0 tenant and the Gateway's Endpoint URL:

```json
{
  "auth0": {
    "domain": "your-tenant.us.auth0.com",
    "client_id": "your-auth0-client-id",
    "client_secret": "your-auth0-client-secret",
    "audience": "your-api-audience",
    "scope": "openid profile email mcp_roles",
    "roles_claim": "https://mcp_gateway/user_roles",
    "redirect_uri": "http://localhost:8501",
    "logout_return_to": "http://localhost:8501"
  },
  "mcp": {
    "enabled": true,
    "servers": {
      "supportchatbot": {
        "transport": "streamable-http",
        "url": "https://mcp-gateway.<id>.cnodes.io/chatbot"
      }
    }
  }
}
```

**Never commit `config.json`** — it holds real credentials and endpoints and
is already git-ignored.

## Running

AWS credentials are read from the standard AWS credential chain:

```bash
uv sync
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_DEFAULT_REGION="us-east-1"
uv run streamlit run src/rbac_shoe_bot/app.py
```

Click **Log in with Auth0** and sign in as one of your test users, then try:

- Signed in as `support-agent`: *"What's the price and stock status of
  product SHOE-001?"* → succeeds (`get_product_details`). *"What do we pay
  our supplier for it?"* → no matching tool for this role.
- Signed in as `merchandiser`: *"What's our cost and margin on SHOE-001?"* →
  succeeds (`get_supplier_cost`). *"Show me my recent orders."* → no matching
  tool for this role.
- Signed in as `readonly-auditor`: sees both cost and order data, but filing
  a ticket is denied by the Gateway.

Click **Log out** and log back in as a different test user to switch
personas — each login forces Auth0's credential prompt fresh. The sidebar's
"Tools discovered" list is the fastest way to confirm your ACL setup: a
`merchandiser` session should show only the two `catalogdata_*` tools.

## Project structure

```
shoe-store-support-chatbot-rbac/
├── src/rbac_shoe_bot/
│   ├── __init__.py
│   ├── app.py             # Streamlit UI: login, sidebar, chat loop
│   ├── auth.py             # Auth0 Authorization Code flow helpers
│   ├── async_runtime.py   # Persistent asyncio loop for Streamlit
│   ├── bedrock_agent.py   # AWS Bedrock Converse agent + tool execution
│   ├── config.py          # Loads config.json into typed config objects
│   └── mcp_manager.py     # Bearer-token authenticated MCP client
├── config.example.json    # Config template (copy to config.json)
├── pyproject.toml
├── uv.lock
├── LICENSE
└── README.md
```

## Additional materials

- [MCP Gateway Tutorial: Connect an AI Agent to Apache Kafka and HTTP Server Backends](https://www.instaclustr.com/blog/mcp-gateway-tutorial-connect-an-ai-agent-to-apache-kafka-and-http-server-backends/) — the prior tutorial this sample builds on.
## License

Released under the [MIT License](LICENSE).
