package com.exchangemingle.backend.model


import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_skills",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "skill_id", "role"])
    ],
    indexes = [
        Index(name = "idx_user_skill_user", columnList = "user_id"),
        Index(name = "idx_user_skill_skill", columnList = "skill_id"),
        Index(name = "idx_user_skill_role", columnList = "role"),
        Index(name = "idx_user_skill_active", columnList = "is_active")
    ]
)
class UserSkill(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @ManyToOne
    @JoinColumn(name = "skill_id", nullable = false)
    var skill: Skill? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var role: SkillRole = SkillRole.LEARNER,

    // Fields only for TEACHER role
    @Column(name = "proficiency_level")
    var proficiencyLevel: Int? = null,  // 1-5 scale

    @Column(name = "hourly_credits")
    var hourlyCredits: Double? = null,  // Credits charged per hour

    @Column(name = "years_experience")
    var yearsOfExperience: Int? = null,

    @Column(name = "teaching_bio", length = 1000)
    var teachingBio: String? = null,  // Specific bio for this skill

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun prePersist() {
        val now = LocalDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun preUpdate() {
        updatedAt = LocalDateTime.now()
    }
}

enum class SkillRole {
    TEACHER,  // User teaches this skill
    LEARNER   // User wants to learn this skill
}