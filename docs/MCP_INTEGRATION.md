
---

## Implemented MCP Bridge MVP

A new service has been added:

```text
tool-mcp-server
```

Port:

```text
8085
```

It exposes a local MCP-style JSON-RPC endpoint:

```text
POST /mcp
```

Supported methods:

```text
tools/list
tools/call
```

Supported tool:

```text
shopping.search
```

The AI orchestrator calls this through:

```text
McpShoppingToolProvider
```

Configuration:

```properties
collabmind.tools.shopping.provider=mcp
collabmind.tools.mcp-shopping.base-url=http://localhost:8085
```

This makes `@ai` a real cross-service tool call instead of only a mock in-process response.

