package com.fiap.pharmacypopular.adapter.db;

import com.fiap.pharmacypopular.adapter.exception.InfrastructureException;
import com.fiap.pharmacypopular.domain.port.PharmacyRepositoryPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PharmacyRepositoryAdapter implements PharmacyRepositoryPort {

    private final DataSource dataSource;

    public PharmacyRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
        public boolean existsByCnpj(String cnpj) {
        String sql = "SELECT 1 FROM pharmacy WHERE cnpj = ? LIMIT 1";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, cnpj);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            throw new InfrastructureException("Failed to query pharmacy by CNPJ: " + cnpj, e);
        }
    }
}
