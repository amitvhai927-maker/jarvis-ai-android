import express from "express";

const app = express();
app.use(express.json({ limit: "20kb" }));

app.get("/health", (_req, res) => res.json({ ok: true, service: "jarvis-ai-backend" }));

app.post("/chat", async (req, res) => {
  try {
    const message = typeof req.body?.message === "string" ? req.body.message.trim() : "";
    const language = ["English", "हिन्दी", "नेपाली"].includes(req.body?.language)
      ? req.body.language : "English";

    if (!message) return res.status(400).json({ error: "message is required" });
    if (message.length > 6000) return res.status(413).json({ error: "message is too long" });

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) return res.status(500).json({ error: "Backend is missing GEMINI_API_KEY" });

    const systemInstruction = `You are JARVIS, a helpful, calm personal AI assistant. Address the user politely as Sir when natural. Respond in ${language}. If the user asks for another language explicitly, follow that request. Do not claim you have controlled the phone or performed actions unless a connected tool actually did so.`;

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": apiKey
        },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: systemInstruction }] },
          contents: [{ role: "user", parts: [{ text: message }] }],
          generationConfig: { temperature: 0.7, maxOutputTokens: 900 }
        }),
        signal: AbortSignal.timeout(40000)
      }
    );

    const data = await response.json();
    if (!response.ok) {
      console.error("Gemini API error status:", response.status);
      return res.status(502).json({ error: "AI provider request failed. Check the backend logs and API key configuration." });
    }

    const reply = data?.candidates?.[0]?.content?.parts
      ?.map((part) => part.text || "")
      .join("")
      .trim();

    if (!reply) return res.status(502).json({ error: "AI provider returned an empty response" });
    return res.json({ reply });
  } catch (error) {
    console.error("Request failed:", error?.message || "unknown error");
    return res.status(500).json({ error: "Internal server error" });
  }
});

const port = Number(process.env.PORT || 3000);
app.listen(port, "0.0.0.0", () => console.log(`JARVIS backend listening on ${port}`));
