package com.fiap.pharmacypopular.adapter.db;

import com.fiap.pharmacypopular.adapter.exception.InfrastructureException;
import com.fiap.pharmacypopular.domain.model.StockEntry;
import com.fiap.pharmacypopular.domain.port.StockRepositoryPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StockRepositoryAdapter implements StockRepositoryPort {

    private static final Logger LOGGER = Logger.getLogger(StockRepositoryAdapter.class.getName());
    private final DataSource dataSource;

    public StockRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void upsertAll(List<StockEntry> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        final String sql = """
                    INSERT INTO pharmacy_medicine_stock (quantity, stock_status, updated_at, medicine_code, pharmacy_id)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (pharmacy_id, medicine_code)
                    DO UPDATE SET
                        quantity = EXCLUDED.quantity,
                        stock_status = EXCLUDED.stock_status,
                        updated_at = EXCLUDED.updated_at
                """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                Timestamp now = Timestamp.from(Instant.now());
                for (StockEntry row : rows) {
                    validate(row);

                    ps.setInt(1, row.quantity());
                    ps.setString(2, row.status().name());
                    ps.setTimestamp(3, now);
                    ps.setString(4, row.medicineCode());
                    ps.setString(5, row.cnpj());
                    ps.addBatch();
                }

                ps.executeBatch();
                connection.commit();

            } catch (Exception ex) {
                rollback(connection);
                throw ex;
            }
        } catch (Exception e) {
            throw new InfrastructureException("Failed to upsert pharmacy stock batch", e);
        }
    }

    private static void validate(StockEntry row) {
        if (row == null) {
            throw new IllegalArgumentException("row is required");
        }
        if (row.cnpj() == null || row.cnpj().isBlank()) {
            throw new IllegalArgumentException("cnpj is required to upsert stock");
        }
        if (row.medicineCode() == null || row.medicineCode().isBlank()) {
            throw new IllegalArgumentException("medicineCode is required to upsert stock");
        }
        if (row.status() == null) {
            throw new IllegalArgumentException("status is required to upsert stock");
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Rollback failed after error", ex);
        }
    }
}


