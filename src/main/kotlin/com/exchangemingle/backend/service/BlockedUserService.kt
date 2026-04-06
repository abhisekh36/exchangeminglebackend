package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.BlockUserRequest
import com.exchangemingle.backend.dto.BlockedUserResponse
import com.exchangemingle.backend.dto.UserResponse
import com.exchangemingle.backend.exception.InvalidRequestException
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.BlockedUser
import com.exchangemingle.backend.repository.BlockedUserRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BlockedUserService(
    private val blockedUserRepository: BlockedUserRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun blockUser(blockerId: Long, request: BlockUserRequest): BlockedUserResponse {
        if (blockerId == request.userId) {
            throw InvalidRequestException("You cannot block yourself")
        }

        val blocker = userRepository.findById(blockerId)
            .orElseThrow { UserNotFoundException("Blocker not found with id: $blockerId") }

        val blocked = userRepository.findById(request.userId)
            .orElseThrow { UserNotFoundException("User to block not found with id: ${request.userId}") }

        if (blockedUserRepository.isBlocked(blocker, blocked)) {
            throw InvalidRequestException("User is already blocked")
        }

        val blockedUser = BlockedUser(
            blocker = blocker,
            blocked = blocked,
            reason = request.reason
        )

        val saved = blockedUserRepository.save(blockedUser)
        return mapToResponse(saved)
    }

    @Transactional
    fun unblockUser(blockerId: Long, blockedUserId: Long) {
        val blocker = userRepository.findById(blockerId)
            .orElseThrow { UserNotFoundException("Blocker not found with id: $blockerId") }

        val blocked = userRepository.findById(blockedUserId)
            .orElseThrow { UserNotFoundException("Blocked user not found with id: $blockedUserId") }

        blockedUserRepository.deleteByBlockerAndBlocked(blocker, blocked)
    }

    fun getBlockedUsers(userId: Long): List<BlockedUserResponse> {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found with id: $userId") }

        return blockedUserRepository.findByBlocker(user)
            .map { mapToResponse(it) }
    }

    fun isBlocked(user1Id: Long, user2Id: Long): Boolean {
        val user1 = userRepository.findById(user1Id)
            .orElseThrow { UserNotFoundException("User not found with id: $user1Id") }

        val user2 = userRepository.findById(user2Id)
            .orElseThrow { UserNotFoundException("User not found with id: $user2Id") }

        return blockedUserRepository.areUsersBlockingEachOther(user1, user2)
    }

    private fun mapToResponse(blockedUser: BlockedUser): BlockedUserResponse {
        return BlockedUserResponse(
            id = blockedUser.id,
            blockedUser = UserResponse(
                id = blockedUser.blocked!!.id,
                email = blockedUser.blocked!!.email,
                name = blockedUser.blocked!!.name,
                credits = blockedUser.blocked!!.credits,
                isEmailVerified = blockedUser.blocked!!.isEmailVerified,
                bio = blockedUser.blocked!!.bio,
                avatar = blockedUser.blocked!!.avatar
            ),
            reason = blockedUser.reason,
            blockedAt = blockedUser.createdAt
        )
    }
}