package com.exchangemingle.backend.exception

class UserAlreadyExistsException(message: String) : RuntimeException(message)
class UserNotFoundException(message: String) : RuntimeException(message)
class TokenRefreshException(message: String) : RuntimeException(message)
class PasswordMismatchException(message: String = "Passwords do not match") : RuntimeException(message)
class SkillAlreadyExistsException(message: String) : RuntimeException(message)
class SkillNotFoundException(message: String) : RuntimeException(message)
class SessionNotFoundException(message: String) : RuntimeException(message)
class InsufficientCreditsException(message: String) : RuntimeException(message)
class InvalidSessionOperationException(message: String) : RuntimeException(message)
class SelfBookingException(message: String = "You cannot book a session with yourself") : RuntimeException(message)

class InvalidVerificationCodeException(message: String = "Invalid or expired verification code") : RuntimeException(message)
class EmailAlreadyVerifiedException(message: String = "Email is already verified") : RuntimeException(message)
class InvalidResetTokenException(message: String = "Invalid or expired reset token") : RuntimeException(message)
class InvalidPasswordException(message: String = "Current password is incorrect") : RuntimeException(message)

class SessionRequestNotFoundException(message: String = "Session request not found") : RuntimeException(message)
class AchievementNotFoundException(message: String = "Achievement not found") : RuntimeException(message)
class NotificationNotFoundException(message: String = "Notification not found") : RuntimeException(message)