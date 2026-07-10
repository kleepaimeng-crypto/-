package com.cabin.user.mapper;

import com.cabin.user.entity.UserRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    long countUsers();

    List<UserRow> findUsers(
            @Param("offset") int offset,
            @Param("pageSize") int pageSize,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection
    );

    UserRow findById(@Param("userId") UUID userId);

    int countByUsername(
            @Param("username") String username,
            @Param("excludeUserId") UUID excludeUserId
    );

    int countByEmail(
            @Param("email") String email,
            @Param("excludeUserId") UUID excludeUserId
    );

    int insertUser(UserRow user);

    int updateUser(
            @Param("userId") UUID userId,
            @Param("username") String username,
            @Param("email") String email,
            @Param("roleCode") String roleCode,
            @Param("status") String status,
            @Param("expectedVersion") int expectedVersion
    );

    int softDeleteUser(
            @Param("userId") UUID userId,
            @Param("operatorId") UUID operatorId,
            @Param("reason") String reason,
            @Param("expectedVersion") int expectedVersion
    );

    List<UUID> lockActiveSuperAdminIds();
}
