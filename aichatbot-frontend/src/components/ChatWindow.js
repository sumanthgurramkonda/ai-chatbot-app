import React, { useState, useEffect, useRef } from 'react';
import { sendChat, streamChat, getConversation } from '../services/api';

export default function ChatWindow({ conversationId, onNewConversation, model, useRag }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [role, setRole] = useState('user');
  const streamingRef = useRef(null);

  useEffect(() => {
    if (conversationId) {
      getConversation(conversationId).then(res => setMessages(res.data.messages || []));
    } else {
      setMessages([]);
    }
  }, [conversationId]);

  const send = async () => {
    if (!input.trim()) return;
    const userMsg = { role, content: input, createdAt: new Date().toISOString() };
    setMessages(prev => [...prev, userMsg]);

    if (useRag || model) {
      // If streaming: use SSE endpoint
      const convId = conversationId || (await createTempConversation()).id;
      stream(convId, input, model, useRag);
    } else {
      const payload = { conversationId, message: input, model, useRag };
      const res = await sendChat(payload);
      setMessages(prev => [...prev, { role: 'assistant', content: res.data.message }]);
      if (!conversationId) onNewConversation(res.data.conversationId);
    }
    setInput('');
  };

  const createTempConversation = async () => {
    // create by sending empty request to server or handle locally
    const res = await sendChat({ message: "", conversationId: null });
    onNewConversation(res.data.conversationId);
    return { id: res.data.conversationId };
  };

  const stream = (convId, message, model, useRag) => {
    if (streamingRef.current) streamingRef.current.close();
    const es = streamChat({ conversationId: convId, message, model, useRag });
    streamingRef.current = es;
    let partial = '';
    es.onmessage = (e) => {
      // e.data is chunk
      if (e.data === '[DONE]') {
        es.close();
        setMessages(prev => [...prev, { role: 'assistant', content: partial }]);
        streamingRef.current = null;
      } else {
        partial += e.data;
        // show typing partial
        const tail = { role: 'assistant', content: partial };
        const withoutTail = messages.filter(m => m.role !== 'assistant' || m.content !== partial); // crude
        setMessages(prev => {
          // show all user + partial assistant
          const nonAssistant = prev.filter(p => p.role !== 'assistant');
          return [...nonAssistant, tail];
        });
      }
    };
    es.onerror = (err) => {
      console.error('SSE error', err);
      es.close();
      streamingRef.current = null;
    };
  };

  return (
    <div className="chat-window">
      <div className="chat-messages">
        {messages.map((m, i) => (
          <div key={i} className={m.role === 'user' ? 'user-msg' : m.role === 'assistant' ? 'ai-msg' : 'system-msg'}>
            <div className="role">{m.role}</div>
            <div className="content">{m.content}</div>
          </div>
        ))}
      </div>
      <div className="controls">
        <select value={role} onChange={e => setRole(e.target.value)}>
          <option value="user">User</option>
          <option value="system">System</option>
        </select>
        <input value={input} onChange={e => setInput(e.target.value)} placeholder="Type message..." />
        <button onClick={send}>Send</button>
      </div>
    </div>
  );
}
