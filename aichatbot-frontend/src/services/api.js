import axios from 'axios';
const API = axios.create({ baseURL: 'http://localhost:8080/api/v1' });

export const listConversations = () => API.get('/conversations');
export const getConversation = (id) => API.get(`/conversations/${id}`);
export const sendChat = (payload) => API.post('/chat', payload);
export const streamChat = ({conversationId, message, model, useRag}) => {
  const params = new URLSearchParams();
  params.append('message', message);
  if (model) params.append('model', model);
  if (useRag) params.append('useRag', useRag);
  const url = `http://localhost:8080/api/v1/stream/${conversationId}?${params.toString()}`;
  return new EventSource(url);
}
export const ingestDocument = (formData) => API.post('/documents/ingest', formData, { headers: {'Content-Type': 'multipart/form-data'} });
