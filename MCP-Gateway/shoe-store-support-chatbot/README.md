# Shoe Store Support ChatBot — MCP Gateway Sample

A small **AWS Bedrock + Streamlit** chatbot that connects to the
**NetApp Instaclustr MCP Gateway** over Streamable HTTP. It shows how an AI agent
reaches live data through a single, secured MCP endpoint instead of hand-wiring
each integration.

This is the companion code for the tutorial *"MCP Gateway Tutorial: Connect an AI
Agent to Apache Kafka and HTTP Server Backends."*

## What it demonstrates

The agent connects to a `supportchatbot` virtual server on the MCP Gateway,
discovers the available tools automatically, and picks the right one for each
plain-language prompt — across two different backends:

- **`ordersapi`** (HTTP Server backend) → `get_orders` lists a shopper's recent orders.
- **`supporttickets`** (Kafka backend) → `submit_request` publishes a support
  request to the `support-requests` topic.

```
AI agent (this app)  ──Streamable HTTP──▶  MCP Gateway (supportchatbot)
                                              ├── ordersapi      → get_orders (HTTP)
                                              └── supporttickets → submit_request (Kafka)
```

## Prerequisites

- An **MCP Gateway** on the Instaclustr platform with a `supportchatbot` virtual
  server exposing the `ordersapi` (HTTP Server) and `supporttickets` (Kafka)
  backends. See the tutorial for setup.
- An **AWS account** with **Amazon Bedrock** model access enabled in `us-east-1`.
- **Python 3.11+** and the [uv](https://docs.astral.sh/uv/) package manager.

## MCP Gateway setup

Before running the chatbot, provision the MCP Gateway and configure the two
backends in the [Instaclustr console](https://console.instaclustr.com) (Terraform
and the Instaclustr API are equally supported).

**1. Provision the MCP Gateway cluster**
Create a new MCP Gateway cluster from the Instaclustr console the same way you
would any other cluster.

**2. Create the virtual server**
Inside the cluster, create an MCP virtual server:
- Name: `supportchatbot`
- Endpoint URL: `/chatbot`

Copy the full Endpoint URL from the Details page
(e.g. `https://mcp-gateway.<id>.cnodes.io/chatbot`) — you'll paste it into
`config.json` in the next section.

**3. Add the HTTP server backend**
Create a backend named `ordersapi` (type: HTTP Server), then add one tool:
- Name: `get_orders` · Type: HTTP Operation · Method: GET

**4. Add the Kafka backend**
First create a Kafka user (`supportmcg`, SASL/SCRAM SHA-256) in your Instaclustr
Kafka cluster. Then create a backend named `supporttickets` (type: Kafka), using
that user's credentials and your Kafka cluster ID. Add one tool:
- Name: `submit_request` · Type: Publish Message · Topic: `support-requests`

**5. Verify**
The `supportchatbot` virtual server should now list two backends —
`ordersapi` (1 tool) and `supporttickets` (1 tool).

## Configuration

This sample reads its settings from a local `config.json`. Start from the provided
template:

```bash
cp config.example.json config.json
```

The only Gateway-relevant part is the `mcp` section — paste your virtual server's
Endpoint URL (from the Instaclustr console) into the `url` field:

```json
{
  "mcp": {
    "enabled": true,
    "servers": {
      "shoestore": {
        "transport": "streamable-http",
        "url": "https://mcp-gateway.<id>.cnodes.io/chatbot"
      }
    }
  }
}
```

The `bedrock` block and AWS credentials are specific to this client, not to the
Gateway. **Do not commit `config.json`** — keep your real endpoint and any
credentials out of version control.

## Running

AWS credentials are read from the standard AWS credential chain:

```bash
uv sync
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_DEFAULT_REGION="us-east-1"
uv run streamlit run src/shoe_bot/app.py
```

Open the app in your browser and try:

- *"Show me my recent orders."* → the agent calls `get_orders` and lists the
  blue sneakers, brown boots, and black shoes.
- *"My black shoes (order 3) don't fit, please log a return request."* → the agent
  calls `submit_request` to publish a support ticket to the Kafka backend.

Each prompt triggers the matching tool automatically — you don't specify tool
names or routing.

## Project structure

```
shoe-store-support-chatbot/
├── src/shoe_bot/
│   ├── __init__.py
│   ├── app.py
│   ├── bedrock_agent.py
│   ├── mcp_manager.py
│   ├── config.py
│   └── async_runtime.py
├── example_mcp_server.py
├── config.example.json
├── pyproject.toml
├── uv.lock
├── LICENSE
└── README.md
```

## License

Released under the [MIT License](LICENSE).
