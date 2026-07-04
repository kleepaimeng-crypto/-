package com.cabin.config.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.type.JdbcType;

class UuidTypeHandlerTests {
    private final UuidTypeHandler handler = new UuidTypeHandler();

    @Test
    void setsUuidAsPostgresOtherType() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        UUID uuid = UUID.randomUUID();

        handler.setNonNullParameter(statement, 1, uuid, JdbcType.OTHER);

        verify(statement).setObject(1, uuid, Types.OTHER);
    }

    @Test
    void readsUuidObject() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        UUID uuid = UUID.randomUUID();
        when(resultSet.getObject("id")).thenReturn(uuid);

        assertThat(handler.getNullableResult(resultSet, "id")).isEqualTo(uuid);
    }

    @Test
    void readsUuidString() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        UUID uuid = UUID.randomUUID();
        when(resultSet.getObject("id")).thenReturn(uuid.toString());

        assertThat(handler.getNullableResult(resultSet, "id")).isEqualTo(uuid);
    }
}
