import React, { useEffect, useState } from 'react';
import { listConversations } from '../services/api';

export default function ConversationList({ onSelect }) {
  const [conversations, setConversations] = useState([]);
  useEffect(() => {
    listConversations().then(res => setConversations(res.data));
  }, []);
  return (
    <div className="conv-list">
      <button onClick={() => onSelect(null)}>New Conversation</button>
      {conversations.map(c => (
        <div key={c.id} className="conv-item" onClick={() => onSelect(c.id)}>
          <div>{c.id.slice(0,8)}</div>
          <div>{c.updatedAt}</div>
        </div>
      ))}
    </div>
  );
}
