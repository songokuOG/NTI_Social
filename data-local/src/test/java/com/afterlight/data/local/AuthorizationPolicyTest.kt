package com.afterlight.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral encoding of the intended Firestore authorization matrix.
 * These tests do not execute the Firebase rules engine; they lock the policy
 * we expect deployed rules to implement.
 */
class AuthorizationPolicyTest {

    data class Party(
        val hostUserId: String,
        val members: List<String>,
        val mediaKey: String? = null
    )

    data class Media(
        val id: String,
        val partyId: String,
        val userId: String
    )

    private fun canReadParty(uid: String?, party: Party): Boolean {
        return uid != null && uid in party.members
    }

    private fun canCreateParty(uid: String?, hostUserId: String, members: List<String>): Boolean {
        return uid != null && hostUserId == uid && members == listOf(uid)
    }

    private fun canUpdateParty(
        uid: String?,
        existing: Party,
        nextHost: String,
        nextMembers: List<String>,
        nextMediaKey: String?
    ): Boolean {
        if (uid == null || uid != existing.hostUserId) return false
        if (nextHost != existing.hostUserId) return false
        if (nextMembers != existing.members) return false
        if (existing.mediaKey != null && nextMediaKey != existing.mediaKey) return false
        return true
    }

    private fun canCreateMedia(uid: String?, party: Party, media: Media): Boolean {
        return uid != null &&
            uid in party.members &&
            media.userId == uid &&
            media.partyId.isNotBlank()
    }

    private fun canDeleteMedia(uid: String?, party: Party, media: Media): Boolean {
        return uid != null && uid in party.members &&
            (uid == media.userId || uid == party.hostUserId)
    }

    @Test
    fun partyRead_memberAllowed_nonMemberDenied() {
        val party = Party(hostUserId = "host", members = listOf("host", "member"))
        assertTrue(canReadParty("member", party))
        assertFalse(canReadParty("stranger", party))
        assertFalse(canReadParty(null, party))
        assertFalse(canReadParty("member", party.copy(members = listOf("host"))))
    }

    @Test
    fun partyCreate_requiresHostIsCreatorAndSoleMember() {
        assertTrue(canCreateParty("u1", "u1", listOf("u1")))
        assertFalse(canCreateParty("u1", "u2", listOf("u1")))
        assertFalse(canCreateParty("u1", "u1", listOf("u1", "u2")))
        assertFalse(canCreateParty(null, "u1", listOf("u1")))
    }

    @Test
    fun partyUpdate_mediaKeyImmutableAndMembersClientLocked() {
        val party = Party(hostUserId = "host", members = listOf("host"), mediaKey = "abc")
        assertTrue(canUpdateParty("host", party, "host", listOf("host"), "abc"))
        assertFalse(canUpdateParty("host", party, "host", listOf("host"), "CHANGED"))
        assertFalse(canUpdateParty("host", party, "other", listOf("host"), "abc"))
        assertFalse(canUpdateParty("host", party, "host", listOf("host", "extra"), "abc"))
        assertFalse(canUpdateParty("member", party, "host", listOf("host"), "abc"))
    }

    @Test
    fun mediaCreate_onlyOwnUserId() {
        val party = Party(hostUserId = "host", members = listOf("host", "member"))
        assertTrue(canCreateMedia("member", party, Media("m1", "p1", "member")))
        assertFalse(canCreateMedia("member", party, Media("m1", "p1", "host")))
        assertFalse(canCreateMedia("stranger", party, Media("m1", "p1", "stranger")))
    }

    @Test
    fun mediaDelete_ownerOrHostOnly() {
        val party = Party(hostUserId = "host", members = listOf("host", "member", "other"))
        val media = Media("m1", "p1", "member")
        assertTrue(canDeleteMedia("member", party, media))
        assertTrue(canDeleteMedia("host", party, media))
        assertFalse(canDeleteMedia("other", party, media))
        assertFalse(canDeleteMedia("stranger", party, media))
    }

    @Test
    fun storageWrite_membersCanDeleteWithoutSizeCheck() {
        val fifteenMb = 15 * 1024 * 1024
        assertTrue(canWriteStorageObject("member", listOf("member"), isDelete = false, sizeBytes = fifteenMb - 1))
        assertFalse(canWriteStorageObject("member", listOf("member"), isDelete = false, sizeBytes = fifteenMb))
        assertTrue(canWriteStorageObject("member", listOf("member"), isDelete = true, sizeBytes = null))
        assertFalse(canWriteStorageObject("stranger", listOf("member"), isDelete = true, sizeBytes = null))
    }

    private fun canWriteStorageObject(
        uid: String?,
        members: List<String>,
        isDelete: Boolean,
        sizeBytes: Int?
    ): Boolean {
        if (uid == null || uid !in members) return false
        if (isDelete) return true
        return sizeBytes != null && sizeBytes < 15 * 1024 * 1024
    }
}
