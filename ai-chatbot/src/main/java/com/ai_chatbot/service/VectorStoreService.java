package com.ai_chatbot.service;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VectorStoreService {

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Upsert a document with an embedding. Uses PGobject to bind jsonb and vector types so the
     * PostgreSQL driver receives the correct types and avoids inline cast issues.
     */
    public void upsertDocument(String id, String title, String content, float[] vector, String metadataJson) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Embedding vector must not be null or empty");
        }
        // Explicit SQL (no inline ::vector cast). We'll bind the vector as a PGobject of type "vector".
        String sql = "INSERT INTO documents(id, title, content, metadata, embedding) VALUES(?, ?, ?, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, content = EXCLUDED.content, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding";

        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql);
            // id as UUID
            ps.setObject(1, UUID.fromString(id));
            ps.setString(2, title);
            ps.setString(3, content == null ? "" : content);

            // metadata as jsonb
            PGobject meta = new PGobject();
            meta.setType("jsonb");
            meta.setValue(metadataJson == null ? "{}" : metadataJson);
            ps.setObject(4, meta);

            // embedding as vector (pgvector/pg extension)
            PGobject vecObj = new PGobject();
            vecObj.setType("vector");
            vecObj.setValue(vectorToSql(vector));
            ps.setObject(5, vecObj);

            return ps;
        });
    }

    /**
     * Convert float[] into the textual vector representation expected by pgvector: "[0.1,0.2,...]".
     */
    public String vectorToSql(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(Float.toString(vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    public void upsertEmbeddingForConversation(String conversationId, String text, double[] embedding) {
        if (embedding == null) throw new IllegalArgumentException("Embedding must not be null");
        upsertEmbeddingForConversation(conversationId, text, toFloatArray(embedding));
    }

    public void upsertEmbeddingForConversation(String conversationId, String text, float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding must not be null or empty");
        }
        String docId = UUID.randomUUID().toString();
        String title = (text == null) ? "" : (text.length() > 100 ? text.substring(0, 100) : text);
        String metadataJson = String.format("{\"conversationId\":\"%s\",\"createdAt\":\"%s\"}", conversationId, Instant.now().toString());
        upsertDocument(docId, title, text, embedding, metadataJson);
    }

    private float[] toFloatArray(double[] src) {
        float[] dst = new float[src.length];
        for (int i = 0; i < src.length; i++) dst[i] = (float) src[i];
        return dst;
    }

    /**
     * Query nearest neighbors using the vector operator. Bind the query vector as a PGobject of type "vector".
     */
    public List<DocumentHit> queryNearest(float[] queryEmbedding, int k) {
        String sql = "SELECT id, title, content, metadata, embedding <-> ? AS distance " +
                "FROM documents ORDER BY embedding <-> ? LIMIT ?";

        return jdbcTemplate.query(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql);
            PGobject vecObj1 = new PGobject();
            vecObj1.setType("vector");
            vecObj1.setValue(vectorToSql(queryEmbedding));
            ps.setObject(1, vecObj1);

            PGobject vecObj2 = new PGobject();
            vecObj2.setType("vector");
            vecObj2.setValue(vectorToSql(queryEmbedding));
            ps.setObject(2, vecObj2);

            ps.setInt(3, k);
            return ps;
        }, (rs, rowNum) -> new DocumentHit(
                UUID.fromString(rs.getString("id")),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getFloat("distance")
        ));
    }

    public record DocumentHit(UUID id, String title, String content, String metadata, float distance) {
    }
}


