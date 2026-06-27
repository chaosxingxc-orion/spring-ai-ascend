import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";

const server = new Server(
  { name: "workmate-mock-oa", version: "0.1.0" },
  { capabilities: { tools: {} } },
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "submit_credit_memo",
      description: "Submit a credit memo to the internal OA approval workflow.",
      inputSchema: {
        type: "object",
        properties: {
          operation: { type: "string", description: "Business operation label" },
          companyName: { type: "string", description: "Enterprise name" },
          customerName: { type: "string", description: "Customer name" },
          creditAmount: { type: "string", description: "Credit limit amount" },
        },
        required: ["companyName", "creditAmount"],
      },
    },
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name !== "submit_credit_memo") {
    return {
      content: [{ type: "text", text: `Unknown tool: ${request.params.name}` }],
      isError: true,
    };
  }
  const args = request.params.arguments ?? {};
  const company = args.companyName || args.customerName || "未知企业";
  const amount = args.creditAmount || args.amount || "未知额度";
  return {
    content: [{ type: "text", text: `OA mock accepted credit memo for ${company} amount ${amount}` }],
  };
});

const transport = new StdioServerTransport();
await server.connect(transport);
