export default function ModelSelector({ value, onChange }) {
  const models = ['gpt-4o', 'gpt-4', 'gpt-3.5-turbo', 'local-llm'];
  return (
    <select value={value} onChange={e => onChange(e.target.value)}>
      {models.map(m => <option key={m} value={m}>{m}</option>)}
    </select>
  );
}
