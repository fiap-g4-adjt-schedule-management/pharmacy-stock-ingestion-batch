package com.fiap.pharmacypopular.adapter.db;

import com.fiap.pharmacypopular.adapter.exception.InfrastructureException;
import com.fiap.pharmacypopular.domain.port.MedicationRepositoryPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class MedicationRepositoryAdapter implements MedicationRepositoryPort {

    private final DataSource dataSource;

    public MedicationRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<String> findCodeByName(String medicineName) {
        final String sql = "SELECT medicine_code FROM medication_name WHERE medicine_name = ? LIMIT 1";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, medicineName);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getString("medicine_code"));
            }

        } catch (SQLException e) {
            throw new InfrastructureException("Failed to query medication by name: " + medicineName, e);
        }
    }
}
