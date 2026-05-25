package com.secondshelf.authservice.repository;

import com.secondshelf.authservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from RefreshToken t
            where t.tokenFamilyId = :tokenFamilyId
              and t.revokedAt is null
            """)
    List<RefreshToken> findAllByTokenFamilyIdAndRevokedAtIsNullForUpdate(
            @Param("tokenFamilyId") String tokenFamilyId
    );
}
