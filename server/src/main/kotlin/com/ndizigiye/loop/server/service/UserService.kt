package com.ndizigiye.loop.server.service

import com.ndizigiye.loop.api.ApiLimits
import com.ndizigiye.loop.api.AuthResponse
import com.ndizigiye.loop.api.LoginRequest
import com.ndizigiye.loop.api.NotificationType
import com.ndizigiye.loop.api.PageDto
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.api.ToggleResultDto
import com.ndizigiye.loop.api.UpdateProfileRequest
import com.ndizigiye.loop.api.UserDto
import com.ndizigiye.loop.server.ApiException
import com.ndizigiye.loop.server.auth.PasswordHasher
import com.ndizigiye.loop.server.auth.TokenService
import com.ndizigiye.loop.server.db.Follows
import com.ndizigiye.loop.server.db.LoopDatabase
import com.ndizigiye.loop.server.db.Users
import com.ndizigiye.loop.server.paging.KeysetOrder
import com.ndizigiye.loop.server.paging.PageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insertIgnoreAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

class UserService(private val db: LoopDatabase, private val tokens: TokenService, private val dto: DtoAssembler) {
    suspend fun register(request: RegisterRequest): AuthResponse {
        val username = UserRules.username(request.username)
        val displayName = UserRules.displayName(request.displayName)
        UserRules.password(request.password)
        val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(request.password) }
        return db.tx {
            // One statement, so two concurrent sign-ups for the same name cannot both pass a check.
            val id = Users.insertIgnoreAndGetId {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[passwordHash] = hashed.hash
                it[passwordSalt] = hashed.salt
                it[avatarColor] = UserRules.avatarColorFor(username)
                it[createdAt] = System.currentTimeMillis()
            }?.value ?: throw ApiException.usernameTaken()
            AuthResponse(tokens.issue(id), dto.user(id, viewerId = id)!!)
        }
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val username = request.username.trim().lowercase()
        val stored = if (!TextRules.canStore(username)) {
            null
        } else {
            db.tx {
                Users.select(Users.id, Users.passwordHash, Users.passwordSalt)
                    .where { Users.username eq username }
                    .firstOrNull()
            }
        }
        // Hash even for unknown usernames so response time does not reveal which ones exist.
        val valid = withContext(Dispatchers.Default) {
            if (stored == null) {
                PasswordHasher.verify(request.password, decoy.hash, decoy.salt)
                false
            } else {
                PasswordHasher.verify(request.password, stored[Users.passwordHash], stored[Users.passwordSalt])
            }
        }
        if (!valid || stored == null) throw ApiException.invalidCredentials()
        val id = stored[Users.id].value
        return AuthResponse(tokens.issue(id), db.tx { dto.user(id, viewerId = id) } ?: throw ApiException.invalidCredentials())
    }

    suspend fun exists(userId: Long): Boolean = db.tx {
        Users.select(Users.id).where { Users.id eq userId }.empty().not()
    }

    suspend fun me(userId: Long): UserDto = db.tx { requireUser(userId) }

    suspend fun updateProfile(userId: Long, request: UpdateProfileRequest): UserDto {
        val displayName = request.displayName?.let(UserRules::displayName)
        val bio = request.bio?.let(UserRules::bio)
        return db.tx {
            if (displayName != null || bio != null) {
                Users.update({ Users.id eq userId }) { row ->
                    displayName?.let { row[Users.displayName] = it }
                    bio?.let { row[Users.bio] = it }
                }
            }
            requireUser(userId)
        }
    }

    /** Points the profile at [fileName] and returns the updated user plus the replaced file name. */
    suspend fun replaceAvatar(userId: Long, fileName: String): Pair<UserDto, String?> = db.tx {
        val previous = Users.select(Users.avatarFile).where { Users.id eq userId }.firstOrNull()?.get(Users.avatarFile)
        Users.update({ Users.id eq userId }) { it[avatarFile] = fileName }
        requireUser(userId) to previous
    }

    suspend fun byUsername(username: String, viewerId: Long?): UserDto = db.tx {
        dto.user(requireId(username), viewerId)!!
    }

    suspend fun followers(username: String, viewerId: Long?, page: PageRequest): PageDto<UserDto> =
        followList(username, viewerId, page, match = Follows.followeeId, other = Follows.followerId)

    suspend fun following(username: String, viewerId: Long?, page: PageRequest): PageDto<UserDto> =
        followList(username, viewerId, page, match = Follows.followerId, other = Follows.followeeId)

    suspend fun setFollowing(userId: Long, username: String, follow: Boolean): ToggleResultDto = db.tx {
        val target = requireId(username)
        if (target == userId) throw ApiException.validation("You can't follow yourself.")
        if (follow) {
            val now = System.currentTimeMillis()
            val inserted = Follows.insertIgnore {
                it[followerId] = userId
                it[followeeId] = target
                it[createdAt] = now
            }.insertedCount > 0
            if (inserted) Notifier.notify(target, userId, NotificationType.FOLLOW, now = now)
        } else {
            val removed = Follows.deleteWhere { (followerId eq userId) and (followeeId eq target) } > 0
            if (removed) Notifier.retract(NotificationType.FOLLOW, actor = userId, recipient = target)
        }
        ToggleResultDto(follow, Follows.selectAll().where { Follows.followeeId eq target }.count())
    }

    private suspend fun followList(
        username: String,
        viewerId: Long?,
        page: PageRequest,
        match: Column<EntityID<Long>>,
        other: Column<EntityID<Long>>,
    ): PageDto<UserDto> = db.tx {
        val target = requireId(username)
        val ids = KeysetOrder(Follows.createdAt, other).ids(Follows, match eq target, page)
        PageDto(dto.userList(ids.rows, viewerId), ids.nextCursor)
    }

    private fun findId(username: String): Long? =
        Users.select(Users.id).where { Users.username eq username.lowercase() }.firstOrNull()?.get(Users.id)?.value

    private fun requireId(username: String): Long = findId(username) ?: throw ApiException.notFound("User not found.")

    private fun requireUser(userId: Long): UserDto =
        dto.user(userId, viewerId = userId) ?: throw ApiException.unauthorized()

    private companion object {
        val decoy: PasswordHasher.Hashed by lazy { PasswordHasher.hash("decoy-password") }
    }
}

/** Validation and normalisation of account fields, per [ApiLimits]. */
object UserRules {
    private val avatarColors = listOf(
        "#6C5CE7", "#00B894", "#0984E3", "#E17055", "#FDCB6E", "#A29BFE",
        "#FAB1A0", "#74B9FF", "#2D3436", "#D35400", "#27AE60", "#8E44AD",
    )

    fun username(raw: String): String {
        val username = raw.trim().lowercase()
        if (!ApiLimits.USERNAME_REGEX.matches(username)) {
            throw ApiException.validation(
                "Usernames are ${ApiLimits.USERNAME_MIN}-${ApiLimits.USERNAME_MAX} characters: letters, numbers, " +
                    "'.' and '_', starting with a letter or number.",
            )
        }
        return username
    }

    fun displayName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty() || name.length > ApiLimits.DISPLAY_NAME_MAX) {
            throw ApiException.validation("Display names are 1-${ApiLimits.DISPLAY_NAME_MAX} characters.")
        }
        return TextRules.requireStorable(name)
    }

    fun password(raw: String) {
        if (raw.length < ApiLimits.PASSWORD_MIN) {
            throw ApiException.validation("Passwords need at least ${ApiLimits.PASSWORD_MIN} characters.")
        }
        if (raw.length > PASSWORD_MAX) throw ApiException.validation("Passwords can be at most $PASSWORD_MAX characters.")
    }

    fun bio(raw: String): String {
        val bio = raw.trim()
        if (bio.length > ApiLimits.BIO_MAX) throw ApiException.validation("Bios can be at most ${ApiLimits.BIO_MAX} characters.")
        return TextRules.requireStorable(bio)
    }

    fun avatarColorFor(username: String): String = avatarColors[Math.floorMod(username.hashCode(), avatarColors.size)]

    private const val PASSWORD_MAX = 256
}
