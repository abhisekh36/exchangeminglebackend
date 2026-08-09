package com.exchangemingle.backend.service

import com.exchangemingle.backend.dto.AvailabilitySlotResponse
import com.exchangemingle.backend.dto.AvailabilityWindowDto
import com.exchangemingle.backend.dto.CreateAvailabilityRequest
import com.exchangemingle.backend.dto.TeacherAvailabilitySummaryResponse
import com.exchangemingle.backend.exception.InvalidRequestException
import com.exchangemingle.backend.exception.UserNotFoundException
import com.exchangemingle.backend.model.TeacherAvailability
import com.exchangemingle.backend.repository.TeacherAvailabilityRepository
import com.exchangemingle.backend.repository.UserRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class TeacherAvailabilityService(
    private val availabilityRepository: TeacherAvailabilityRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    @CacheEvict(value = ["discovery-teachers"], allEntries = true, cacheManager = "redisCacheManager")
    fun addSlot(teacherId: Long, request: CreateAvailabilityRequest): AvailabilitySlotResponse {
        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found: $teacherId") }

        // request.slotStart/slotEnd are Instants (true UTC instants). Everywhere
        // else in this service/entity treats LocalDateTime as "UTC wall-clock
        // digits" (see mapToResponse below, and getAvailabilitySummaryForTeacher),
        // so we convert here once, at the boundary, to stay consistent with that.
        val slotStart = LocalDateTime.ofInstant(request.slotStart, ZoneOffset.UTC)
        val slotEnd = LocalDateTime.ofInstant(request.slotEnd, ZoneOffset.UTC)

        if (slotStart.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw InvalidRequestException("Slot start time must be in the future")
        }
        if (!slotEnd.isAfter(slotStart)) {
            throw InvalidRequestException("Slot end time must be after start time")
        }
        val durationMinutes = java.time.Duration.between(slotStart, slotEnd).toMinutes()
        if (durationMinutes < 15 || durationMinutes > 180) {
            throw InvalidRequestException("Slot duration must be between 15 and 180 minutes")
        }

        // ── Overlap check: teacher cannot publish two overlapping slots ──────
        val overlapping = availabilityRepository.findOverlappingSlots(
            teacher, slotStart, slotEnd
        )
        if (overlapping.isNotEmpty()) {
            val existing = overlapping.first()
            val fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
            throw InvalidRequestException(
                "You already have a slot from ${existing.slotStart.format(fmt)} to ${existing.slotEnd.format(fmt)} " +
                        "on that day. Please choose a different time."
            )
        }

        val slot = TeacherAvailability(
            teacher = teacher,
            slotStart = slotStart,
            slotEnd = slotEnd,
            note = request.note
        )
        val saved = availabilityRepository.save(slot)
        return mapToResponse(saved)
    }

    @Transactional
    fun deleteSlot(teacherId: Long, slotId: Long) {
        val slot = availabilityRepository.findById(slotId)
            .orElseThrow { InvalidRequestException("Slot not found: $slotId") }
        if (slot.teacher?.id != teacherId) {
            throw InvalidRequestException("You can only delete your own availability slots")
        }
        if (slot.isBooked) {
            throw InvalidRequestException("Cannot delete a booked slot")
        }
        availabilityRepository.delete(slot)
    }

    fun getMySlots(teacherId: Long): List<AvailabilitySlotResponse> {
        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found: $teacherId") }
        return availabilityRepository.findAllByTeacher(teacher).map { mapToResponse(it) }
    }

    fun getAvailableSlotsForTeacher(teacherId: Long): List<AvailabilitySlotResponse> {
        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found: $teacherId") }
        return availabilityRepository.findAvailableByTeacher(teacher, LocalDateTime.now(ZoneOffset.UTC))
            .map { mapToResponse(it) }
    }

    @Transactional
    fun markSlotBooked(slotId: Long) {
        val slot = availabilityRepository.findById(slotId).orElse(null) ?: return
        slot.isBooked = true
        availabilityRepository.save(slot)
    }

    /**
     * Returns structured availability summary for a teacher.
     * The booking screen uses this to gray out dates/hours outside the teacher's slots.
     *
     * Windows are returned as full ISO instant strings, NOT raw hour/minute
     * ints — the backend doesn't know the learner's device timezone, so only
     * the app (which does) can correctly turn an instant into a local hour.
     */
    fun getAvailabilitySummaryForTeacher(teacherId: Long): TeacherAvailabilitySummaryResponse {
        val teacher = userRepository.findById(teacherId)
            .orElseThrow { UserNotFoundException("Teacher not found: $teacherId") }
        val slots = availabilityRepository.findAvailableByTeacher(teacher, LocalDateTime.now(ZoneOffset.UTC))

        val windows = slots.map { slot ->
            AvailabilityWindowDto(
                slotId       = slot.id,
                slotStartIso = slot.slotStart.toInstant(ZoneOffset.UTC).toString(),
                slotEndIso   = slot.slotEnd.toInstant(ZoneOffset.UTC).toString(),
                note         = slot.note
            )
        }
        return TeacherAvailabilitySummaryResponse(availableWindows = windows)
    }

    private fun mapToResponse(slot: TeacherAvailability) = AvailabilitySlotResponse(
        id = slot.id,
        slotStart = slot.slotStart.toInstant(ZoneOffset.UTC).toString(),
        slotEnd = slot.slotEnd.toInstant(ZoneOffset.UTC).toString(),
        isBooked = slot.isBooked,
        note = slot.note
    )
}