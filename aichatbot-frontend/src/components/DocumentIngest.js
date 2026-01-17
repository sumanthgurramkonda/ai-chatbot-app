import React, { useState } from 'react';
import { ingestDocument } from '../services/api';

export default function DocumentIngest() {
  const [file, setFile] = useState(null);
  const upload = async () => {
    if (!file) return;
    const fd = new FormData();
    fd.append('file', file);
    const res = await ingestDocument(fd);
    alert('Ingested: ' + JSON.stringify(res.data));
  };
  return (
    <div>
      <input type="file" onChange={e => setFile(e.target.files[0])} />
      <button onClick={upload}>Ingest Document</button>
    </div>
  );
}
