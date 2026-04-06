package com.exchangemingle.backend.repository

import com.exchangemingle.backend.model.BlockedUser
import com.exchangemingle.backend.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface BlockedUserRepository : JpaRepository<BlockedUser, Long> {

    fun findByBlocker(blocker: User): List<BlockedUser>

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BlockedUser b WHERE b.blocker = :blocker AND b.blocked = :blocked")
    fun isBlocked(@Param("blocker") blocker: User, @Param("blocked") blocked: User): Boolean

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BlockedUser b WHERE (b.blocker = :user1 AND b.blocked = :user2) OR (b.blocker = :user2 AND b.blocked = :user1)")
    fun areUsersBlockingEachOther(@Param("user1") user1: User, @Param("user2") user2: User): Boolean

    fun deleteByBlockerAndBlocked(blocker: User, blocked: User)
}