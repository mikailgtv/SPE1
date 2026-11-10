package ir.guardianlink.server

import java.sql.Connection
import java.time.OffsetDateTime
import java.util.UUID

// ================= rows =================
data class UserRow(val id: UUID, val email: String, val passwordHash: String, val createdAt: OffsetDateTime)
data class FamilyRow(val id: UUID, val name: String, val createdAt: OffsetDateTime)
data class MemberRow(val id: UUID, val familyId: UUID, val userId: UUID, val role: String)
data class DeviceRow(
    val id: UUID,
    val familyId: UUID,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
    val batteryLevel: Int?,
    val lastSeen: OffsetDateTime?,
    val status: String,
    val installId: String?,
    val secretHash: String?,
    val createdAt: OffsetDateTime,
)
data class PairingRow(
    val id: UUID,
    val familyId: UUID,
    val parentId: UUID,
    val codeHash: String,
    val status: String,
    val expiresAt: OffsetDateTime,
    val exchangeHash: String?,
    val exchangeExpiresAt: OffsetDateTime?,
    val usedAt: OffsetDateTime?,
    val deviceId: UUID?,
)
data class SessionRow(val id: UUID, val ownerId: UUID, val refreshHash: String, val expiresAt: OffsetDateTime, val revokedAt: OffsetDateTime?)

// ================= repos =================
class UserRepo(private val db: Db) {
    fun byEmail(email: String): UserRow? =
        db.one(
            "SELECT id,email,password_hash,created_at FROM users WHERE lower(email)=lower(?)",
            email,
        ) { it.userRow() }

    fun byId(id: UUID): UserRow? =
        db.one("SELECT id,email,password_hash,created_at FROM users WHERE id=?", id) { it.userRow() }

    fun create(conn: Connection, email: String, hash: String): UUID =
        conn.execReturningUuid(
            "INSERT INTO users(email,password_hash) VALUES(?,?) RETURNING id",
            email.lowercase(), hash,
        )
}

private fun java.sql.ResultSet.userRow() = UserRow(uuid("id"), getString("email"), getString("password_hash"), odt("created_at"))

class FamilyRepo(private val db: Db) {
    fun create(conn: Connection, name: String): UUID =
        conn.execReturningUuid("INSERT INTO families(name) VALUES(?) RETURNING id", name)

    fun byId(id: UUID): FamilyRow? =
        db.one("SELECT id,name,created_at FROM families WHERE id=?", id) {
            FamilyRow(it.uuid("id"), it.getString("name"), it.odt("created_at"))
        }

    fun familyOfUser(userId: UUID): FamilyRow? =
        db.one(
            """SELECT f.id,f.name,f.created_at FROM families f
               JOIN family_members m ON m.family_id=f.id WHERE m.user_id=?""",
            userId,
        ) { FamilyRow(it.uuid("id"), it.getString("name"), it.odt("created_at")) }

    fun addMember(conn: Connection, familyId: UUID, userId: UUID, role: String): UUID =
        conn.execReturningUuid(
            "INSERT INTO family_members(family_id,user_id,role) VALUES(?,?,?) RETURNING id",
            familyId, userId, role,
        )

    fun members(familyId: UUID): List<MemberRow> =
        db.query(
            "SELECT id,family_id,user_id,role FROM family_members WHERE family_id=?",
            familyId,
        ) { MemberRow(it.uuid("id"), it.uuid("family_id"), it.uuid("user_id"), it.getString("role")) }
}

class UserSessionRepo(private val db: Db) {
    fun create(conn: Connection, userId: UUID, refreshHash: String, expiresAt: OffsetDateTime): UUID =
        conn.execReturningUuid(
            "INSERT INTO user_sessions(user_id,refresh_hash,expires_at) VALUES(?,?,?) RETURNING id",
            userId, refreshHash, expiresAt,
        )

    fun byHash(hash: String): SessionRow? =
        db.one(
            "SELECT id,user_id,refresh_hash,expires_at,revoked_at FROM user_sessions WHERE refresh_hash=?",
            hash,
        ) { it.sessionRow() }

    fun byId(id: UUID): SessionRow? =
        db.one(
            "SELECT id,user_id,refresh_hash,expires_at,revoked_at FROM user_sessions WHERE id=?",
            id,
        ) { it.sessionRow() }

    fun revoke(conn: Connection, id: UUID) {
        conn.exec("UPDATE user_sessions SET revoked_at=now() WHERE id=? AND revoked_at IS NULL", id)
    }
}

private fun java.sql.ResultSet.sessionRow() =
    SessionRow(uuid("id"), uuid("user_id"), getString("refresh_hash"), odt("expires_at"), odtN("revoked_at"))

private fun java.sql.ResultSet.deviceSessionRow() =
    SessionRow(uuid("id"), uuid("device_id"), getString("refresh_hash"), odt("expires_at"), odtN("revoked_at"))

class DeviceSessionRepo(private val db: Db) {
    fun create(conn: Connection, deviceId: UUID, refreshHash: String, expiresAt: OffsetDateTime): UUID =
        conn.execReturningUuid(
            "INSERT INTO device_sessions(device_id,refresh_hash,expires_at) VALUES(?,?,?) RETURNING id",
            deviceId, refreshHash, expiresAt,
        )

    fun byHash(hash: String): SessionRow? =
        db.one(
            "SELECT id,device_id,refresh_hash,expires_at,revoked_at FROM device_sessions WHERE refresh_hash=?",
            hash,
        ) { it.deviceSessionRow() }

    fun byId(id: UUID): SessionRow? =
        db.one(
            "SELECT id,device_id,refresh_hash,expires_at,revoked_at FROM device_sessions WHERE id=?",
            id,
        ) { it.deviceSessionRow() }

    fun revoke(conn: Connection, id: UUID) {
        conn.exec("UPDATE device_sessions SET revoked_at=now() WHERE id=? AND revoked_at IS NULL", id)
    }

    fun revokeAllForDevice(conn: Connection, deviceId: UUID) {
        conn.exec(
            "UPDATE device_sessions SET revoked_at=now() WHERE device_id=? AND revoked_at IS NULL",
            deviceId,
        )
    }
}

class PairingRepo(private val db: Db) {
    fun create(conn: Connection, familyId: UUID, parentId: UUID, codeHash: String, expiresAt: OffsetDateTime): UUID =
        conn.execReturningUuid(
            "INSERT INTO pairing_sessions(family_id,parent_id,code_hash,expires_at) VALUES(?,?,?,?) RETURNING id",
            familyId, parentId, codeHash, expiresAt,
        )

    fun byCodeHash(hash: String): PairingRow? =
        db.one("SELECT * FROM pairing_sessions WHERE code_hash=?", hash) { it.pairingRow() }

    fun byId(id: UUID): PairingRow? =
        db.one("SELECT * FROM pairing_sessions WHERE id=?", id) { it.pairingRow() }

    fun activeForFamily(familyId: UUID): PairingRow? =
        db.one(
            "SELECT * FROM pairing_sessions WHERE family_id=? AND status IN ('active','exchanging') AND expires_at>now() ORDER BY created_at DESC LIMIT 1",
            familyId,
        ) { it.pairingRow() }

    fun markExchanging(conn: Connection, id: UUID, exchangeHash: String, exchangeExpiresAt: OffsetDateTime) {
        conn.exec(
            "UPDATE pairing_sessions SET status='exchanging', exchange_token_hash=?, exchange_expires_at=? WHERE id=?",
            exchangeHash, exchangeExpiresAt, id,
        )
    }

    fun markUsed(conn: Connection, id: UUID, deviceId: UUID) {
        conn.exec(
            "UPDATE pairing_sessions SET status='used', used_at=now(), device_id=? WHERE id=?",
            deviceId, id,
        )
    }

    fun markExpired(conn: Connection, id: UUID) {
        conn.exec("UPDATE pairing_sessions SET status='expired' WHERE id=?", id)
    }
}

private fun java.sql.ResultSet.pairingRow() = PairingRow(
    uuid("id"), uuid("family_id"), uuid("parent_id"), getString("code_hash"), getString("status"),
    odt("expires_at"), getString("exchange_token_hash"), odtN("exchange_expires_at"), odtN("used_at"), uuidN("device_id"),
)

class DeviceRepo(private val db: Db) {
    fun create(
        conn: Connection,
        familyId: UUID,
        deviceName: String,
        manufacturer: String,
        model: String,
        androidVersion: String,
        appVersion: String,
        installId: String,
        secretHash: String,
    ): UUID = conn.execReturningUuid(
        """INSERT INTO devices(family_id,device_name,manufacturer,model,android_version,app_version,install_id,device_secret_hash,last_seen)
           VALUES(?,?,?,?,?,?,?,?,now()) RETURNING id""",
        familyId, deviceName, manufacturer, model, androidVersion, appVersion, installId, secretHash,
    )

    fun byId(id: UUID): DeviceRow? = db.one("SELECT * FROM devices WHERE id=?", id) { it.deviceRow() }

    fun activeByInstallId(installId: String): DeviceRow? =
        db.one("SELECT * FROM devices WHERE install_id=? AND status='active'", installId) { it.deviceRow() }

    fun listActiveByFamily(familyId: UUID): List<DeviceRow> =
        db.query(
            "SELECT * FROM devices WHERE family_id=? AND status='active' ORDER BY created_at",
            familyId,
        ) { it.deviceRow() }

    fun heartbeat(conn: Connection, id: UUID, battery: Int?, appVersion: String?, androidVersion: String?, model: String?) {
        conn.exec(
            """UPDATE devices SET last_seen=now(),
               battery_level=COALESCE(?,battery_level),
               app_version=COALESCE(NULLIF(?,''),app_version),
               android_version=COALESCE(NULLIF(?,''),android_version),
               model=COALESCE(NULLIF(?,''),model)
               WHERE id=?""",
            battery, appVersion, androidVersion, model, id,
        )
    }

    fun markRemoved(conn: Connection, id: UUID) {
        conn.exec(
            "UPDATE devices SET status='removed', device_secret_hash=NULL WHERE id=?",
            id,
        )
    }
}

private fun java.sql.ResultSet.deviceRow() = DeviceRow(
    uuid("id"), uuid("family_id"), getString("device_name"), getString("manufacturer"), getString("model"),
    getString("android_version"), getString("app_version"), intN("battery_level"), odtN("last_seen"),
    getString("status"), getString("install_id"), getString("device_secret_hash"), odt("created_at"),
)
