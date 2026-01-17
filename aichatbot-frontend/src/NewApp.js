import React, { useState, useEffect, useRef } from "react";
import axios from "axios";
import "./App.css";

const API_BASE = "http://localhost:8080/api/v1";

export default function App() {
  const [conversationId, setConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [models, setModels] = useState([]);
  const [selectedModel, setSelectedModel] = useState("");
  const [useRag, setUseRag] = useState(false);
  const [documents, setDocuments] = useState([]);
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(false);

  const messagesEndRef = useRef(null);

  // Scroll to bottom when messages update
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };
  useEffect(scrollToBottom, [messages]);

  // Load conversations
  useEffect(() => {
    axios.get(`${API_BASE}/conversations`)
      .then(res => setConversations(Array.isArray(res.data) ? res.data : []))
      .catch(err => console.error("Failed to load conversations", err));
  }, []);

  // Load models
  useEffect(() => {
    axios.get(`${API_BASE}/models`)
      .then(res => setModels(res.data || []))
      .catch(err => console.error("Failed to load models", err));
  }, []);

  // Send message (non-streaming)
  const sendMessage = async () => {
    if (!input.trim()) return;

    const userMsg = { role: "user", content: input };
    setMessages(prev => [...prev, userMsg]);
    setLoading(true);

    try {
      const res = await axios.post(`${API_BASE}/chat`, {
        conversationId,
        message: input,
        model: selectedModel,
        useRag,
        k: 3
      });

      setConversationId(res.data.conversationId);
      setMessages(prev => [
        ...prev,
        { role: "assistant", content: res.data.message }
      ]);
      setInput("");
    } catch (err) {
      console.error(err);
      alert("Failed to send message");
    } finally {
      setLoading(false);
    }
  };

  // Stream message via SSE
  const streamMessage = (msg) => {
    if (!msg.trim()) return;
    const userMsg = { role: "user", content: msg };
    setMessages(prev => [...prev, userMsg]);
    setInput("");

    const url = new URL(`${API_BASE}/stream/${conversationId || ""}`);
    url.searchParams.append("message", msg);
    if (selectedModel) url.searchParams.append("model", selectedModel);
    url.searchParams.append("useRag", useRag);

    const evtSource = new EventSource(url);
    let buffer = "";

    evtSource.onmessage = (event) => {
      buffer += event.data;
      setMessages(prev => {
        const newMsgs = [...prev];
        const lastMsg = newMsgs[newMsgs.length - 1];
        if (lastMsg?.role === "assistant" && !lastMsg.streamComplete) {
          lastMsg.content = buffer;
        } else {
          newMsgs.push({ role: "assistant", content: buffer, streamComplete: false });
        }
        return [...newMsgs];
      });
    };

    evtSource.onerror = () => {
      setMessages(prev => prev.map(m => m.role === "assistant" && !m.streamComplete ? {...m, streamComplete: true} : m));
      evtSource.close();
      // Save conversationId if assigned
      if (!conversationId) setConversationId(""); // Backend generates it automatically
    };
  };

  // Load conversation
  const loadConversation = (conv) => {
    setConversationId(conv.id);
    setMessages(conv.messages || []);
  };

  // Upload RAG documents
  const handleDocumentUpload = async () => {
    if (!documents.length) return alert("Select files first!");
    const formData = new FormData();
    documents.forEach(file => formData.append("files", file));

    try {
      await axios.post(`${API_BASE}/documents`, formData, {
        headers: { "Content-Type": "multipart/form-data" }
      });
      alert("Documents uploaded successfully");
      setDocuments([]);
    } catch (err) {
      console.error(err);
      alert("Failed to upload documents");
    }
  };

  // Save conversation manually
  const saveConversation = async () => {
    if (!messages.length) return alert("No messages to save");
    try {
      const res = await axios.post(`${API_BASE}/conversation`, { messages });
      setConversations(prev => [...prev, res.data]);
      alert("Conversation saved");
    } catch (err) {
      console.error(err);
      alert("Failed to save conversation");
    }
  };

  return (
    <div className="app">
      <aside className="sidebar">
        <h3>Conversations</h3>
        {conversations.map(c => (
          <div
            key={c.id}
            className="conversation"
            onClick={() => loadConversation(c)}
          >
            {c.id.substring(0, 8)}
          </div>
        ))}
      </aside>

      <main className="chat">
        <header>
          <select
            value={selectedModel}
            onChange={e => setSelectedModel(e.target.value)}
          >
            <option value="">Select Model</option>
            {models.map(m => (
              <option key={m.id || m.name} value={m.name}>{m.name}</option>
            ))}
          </select>

          <label>
            <input
              type="checkbox"
              checked={useRag}
              onChange={e => setUseRag(e.target.checked)}
            />
            Use RAG
          </label>
        </header>

        <div className="messages">
          {messages.map((m, i) => (
            <div key={i} className={`message ${m.role}`}>
              {m.content}
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        <footer>
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            placeholder="Ask something..."
            onKeyDown={e => e.key === "Enter" ? streamMessage(input) : null}
            disabled={loading}
          />
          <button onClick={() => streamMessage(input)} disabled={loading}>
            {loading ? "Sending..." : "Send"}
          </button>
        </footer>

        {useRag && (
          <div className="rag-section">
            <input
              type="file"
              multiple
              onChange={e => setDocuments([...documents, ...e.target.files])}
            />
            <button onClick={handleDocumentUpload}>Upload Documents</button>
          </div>
        )}

        <div className="save-section">
          <button onClick={saveConversation}>Save Conversation</button>
        </div>
      </main>
    </div>
  );
}
