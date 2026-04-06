package com.exchangemingle.backend.controller

import com.exchangemingle.backend.dto.AvailabilitySlotResponse
import com.exchangemingle.backend.dto.CreateAvailabilityRequest
import com.exchangemingle.backend.dto.TeacherAvailabilitySummaryResponse
import com.exchangemingle.backend.service.JwtService
import com.exchangemingle.backend.service.TeacherAvailabilityService
import com.exchangemingle.backend.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/availability")
class TeacherAvailabilityController(
    private val availabilityService: TeacherAvailabilityService,
    private val userService: UserService,
    private val jwtService: JwtService
) {

    /** Teacher adds an available time slot */
    @PostMapping
    fun addSlot(
        @RequestHeader("Authorization") authHeader: String,
        @RequestBody request: CreateAvailabilityRequest
    ): ResponseEntity<AvailabilitySlotResponse> {
        val teacher = userService.findByEmail(jwtService.extractUsername(authHeader.substring(7)))
        val slot = availabilityService.addSlot(teacher.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(slot)
    }

    /** Teacher deletes an availability slot */
    @DeleteMapping("/{slotId}")
    fun deleteSlot(
        @RequestHeader("Authorization") authHeader: String,
        @PathVariable slotId: Long
    ): ResponseEntity<Map<String, String>> {
        val teacher = userService.findByEmail(jwtService.extractUsername(authHeader.substring(7)))
        availabilityService.deleteSlot(teacher.id, slotId)
        return ResponseEntity.ok(mapOf("message" to "Slot deleted successfully"))
    }

    /** Teacher views their own slots (all) */
    @GetMapping("/my-slots")
    fun getMySlots(
        @RequestHeader("Authorization") authHeader: String
    ): ResponseEntity<List<AvailabilitySlotResponse>> {
        val teacher = userService.findByEmail(jwtService.extractUsername(authHeader.substring(7)))
        return ResponseEntity.ok(availabilityService.getMySlots(teacher.id))
    }

    /** Anyone can view a teacher's available (not booked) slots */
    @GetMapping("/teacher/{teacherId}")
    fun getAvailableSlots(
        @PathVariable teacherId: Long
    ): ResponseEntity<List<AvailabilitySlotResponse>> {
        return ResponseEntity.ok(availabilityService.getAvailableSlotsForTeacher(teacherId))
    }

    /**
     * Returns a structured summary of a teacher's available windows.
     * Used by the learner booking screen to gray out unavailable dates/times.
     */
    @GetMapping("/teacher/{teacherId}/summary")
    fun getAvailabilitySummary(
        @PathVariable teacherId: Long
    ): ResponseEntity<TeacherAvailabilitySummaryResponse> {
        return ResponseEntity.ok(availabilityService.getAvailabilitySummaryForTeacher(teacherId))
    }
}