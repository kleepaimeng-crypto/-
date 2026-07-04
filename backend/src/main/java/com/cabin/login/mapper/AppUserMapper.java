package com.cabin.login.mapper;

import com.cabin.login.entity.AppUser;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppUserMapper {
    @Select("""
            SELECT id, username, password_hash, email, role_code, status, last_login_at
            FROM app_user
            WHERE lower(username) = lower(#{username})
            """)
    AppUser findByUsername(@Param("username") String username);

    @Select("""
            SELECT id, username, password_hash, email, role_code, status, last_login_at
            FROM app_user
            WHERE id = #{id}
            """)
    AppUser findById(@Param("id") UUID id);

    @Select("""
            SELECT count(*)
            FROM app_user
            WHERE lower(username) = lower(#{username})
            """)
    int countByUsername(@Param("username") String username);

    @Insert("""
            INSERT INTO app_user (id, username, password_hash, email, role_code, status)
            VALUES (#{id}, #{username}, #{passwordHash}, #{email,jdbcType=VARCHAR}, #{roleCode}, #{status})
            """)
    int insert(AppUser user);

    @Update("""
            UPDATE app_user
            SET last_login_at = now(),
                version = version + 1
            WHERE id = #{id}
            """)
    int updateLastLoginAt(@Param("id") UUID id);
}
