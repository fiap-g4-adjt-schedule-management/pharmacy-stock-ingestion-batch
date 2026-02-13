package com.fiap.pharmacypopular.adapter.db;

import com.fiap.pharmacypopular.adapter.exception.InfrastructureException;
import com.fiap.pharmacypopular.domain.model.IngestionControlModel;
import com.fiap.pharmacypopular.domain.port.IngestionControlRepositoryPort;
import com.fiap.pharmacypopular.domain.model.IngestStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

public class IngestionControlAdapter implements IngestionControlRepositoryPort {

    private final DataSource dataSource;

    public IngestionControlAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<IngestionControlModel> findByBlobPathAndEtag(String blobPath, String etag) {
        final String sql = "SELECT id, status FROM file_ingestion_control  WHERE blob_path = ? AND etag = ? LIMIT 1";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, blobPath);
            ps.setString(2, etag);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long id = rs.getLong("id");
                IngestStatus status = IngestStatus.valueOf(rs.getString("status"));
                return Optional.of(new IngestionControlModel(id, status));
            }
        } catch (SQLException e) {
            throw new InfrastructureException("Failed to query ingestion control by blob_path and etag", e);
        }
    }

    @Override
    public Optional<Long> tryStartProcessing(String blobPath, String etag, String fileName, String cnpj, LocalDate referenceDate) {
        final String sql = "INSERT INTO file_ingestion_control (blob_path, etag, file_name, cnpj, reference_date, status, received_at) " +
                "VALUES (?, ?, ?, ?, ?, 'PROCESSING', now()) ON CONFLICT (blob_path, etag) DO NOTHING RETURNING id";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, blobPath);
            ps.setString(2, etag);
            ps.setString(3, fileName);
            ps.setString(4, cnpj);
            ps.setDate(5, Date.valueOf(referenceDate));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new InfrastructureException("Failed to create ingestion control record (PROCESSING)", e);
        }
    }

    @Override
    public void markProcessed(long id) {
        final String sql = "UPDATE file_ingestion_control SET status = 'PROCESSED', processed_at = now(), error_reason = NULL WHERE id = ?";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new InfrastructureException("Failed to mark ingestion as PROCESSED (id=" + id + ")", e);
        }

    }

    @Override
    public void markFailed(long id, String errorReason) {
        final String sql = "UPDATE file_ingestion_control SET status = 'FAILED', processed_at = now(), error_reason = ? WHERE id = ?";

        String reason = (errorReason == null) ? "Unknown error" : errorReason;
        if (reason.length() > 1000) reason = reason.substring(0, 1000);

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, reason);
            ps.setLong(2, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new InfrastructureException("Failed to mark ingestion as FAILED (id=" + id + ")", e);
        }
    }
}
