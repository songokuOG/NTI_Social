package com.afterlight.feature.party.data

import android.content.Context
import android.util.Log
import com.afterlight.core.network.ConnectivityObserver
import com.afterlight.core.security.PartyKeyCodec
import com.afterlight.core.security.PartyKeyStore
import com.afterlight.data.local.MediaEntityFactory
import com.afterlight.data.local.MediaFilePaths
import com.afterlight.data.local.dao.MediaDao
import com.afterlight.data.local.dao.PartyDao
import com.afterlight.data.local.model.MediaEntity
import com.afterlight.data.local.model.PartyEntity
import com.afterlight.data.remote.firebase.FirebasePartyService
import com.afterlight.feature.party.domain.PartyRepository
import com.afterlight.feature.party.worker.PartyExpirationScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-backed implementation of PartyRepository.
 * Syncs parties and party media metadata into Room, and imports the shared media key.
 */
@Singleton
class FirebasePartyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val partyService: FirebasePartyService,
    private val partyDao: PartyDao,
    private val mediaDao: MediaDao,
    private val partyKeyStore: PartyKeyStore,
    private val expirationScheduler: PartyExpirationScheduler,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val connectivityObserver: ConnectivityObserver
) : PartyRepository {

    private companion object {
        const val TAG = "FirebasePartyRepo"
    }

    private var partyListener: ListenerRegistration? = null
    private val mediaListeners = mutableMapOf<String, ListenerRegistration>()
    private var isSyncing = false
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val userId = firebaseAuth.currentUser?.uid
            if (userId != null) {
                startSyncing(userId)
            } else {
                stopSyncing()
            }
        }

        repositoryScope.launch {
            connectivityObserver.isConnected.collectLatest { connected ->
                try {
                    if (connected) {
                        Log.d(TAG, "Device online. Enabling Firestore network.")
                        firestore.enableNetwork().await()
                    } else {
                        Log.d(TAG, "Device offline. Disabling Firestore network.")
                        firestore.disableNetwork().await()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error adjusting Firestore network state: ${e.message}")
                }
            }
        }
    }

    private fun startSyncing(userId: String) {
        if (isSyncing) return
        isSyncing = true
        Log.d(TAG, "Starting real-time Firestore sync for authenticated user")

        partyListener = firestore.collection("parties")
            .whereArrayContains("members", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in snapshot listener: ${error.message}", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    repositoryScope.launch {
                        try {
                            val activeParties = mutableListOf<PartyEntity>()
                            val activePartyIds = mutableSetOf<String>()
                            val snapshotIds = snapshot.documents.map { it.id }.toSet()
                            val now = Clock.System.now()

                            for (doc in snapshot.documents) {
                                val id = doc.id
                                val name = doc.getString("name") ?: ""
                                val hostUserId = doc.getString("hostUserId") ?: ""
                                val createdAtTimestamp = doc.getTimestamp("createdAt")
                                val expiresAtTimestamp = doc.getTimestamp("expiresAt")
                                val isActive = doc.getBoolean("isActive") ?: true
                                val mediaKey = doc.getString("mediaKey")

                                val createdAt = createdAtTimestamp?.let { 
                                    Instant.fromEpochMilliseconds(it.toDate().time) 
                                } ?: now
                                
                                val expiresAt = expiresAtTimestamp?.let { 
                                    Instant.fromEpochMilliseconds(it.toDate().time) 
                                } ?: now

                                if (isActive && expiresAt > now) {
                                    val party = PartyEntity(
                                        id = id,
                                        name = name,
                                        hostUserId = hostUserId,
                                        createdAt = createdAt,
                                        expiresAt = expiresAt,
                                        isDeleted = false
                                    )
                                    activeParties.add(party)
                                    activePartyIds.add(id)
                                    expirationScheduler.scheduleExpiration(id, expiresAt)
                                    ensureSharedMediaKey(id, hostUserId, mediaKey)
                                } else {
                                    purgeLocalPartyAccess(id)
                                }
                            }

                            val localActive = partyDao.getActivePartiesOnce(now)
                            localActive.filter { it.id !in snapshotIds }.forEach { stale ->
                                purgeLocalPartyAccess(stale.id)
                            }

                            if (activeParties.isNotEmpty()) {
                                partyDao.insertAll(activeParties)
                            }

                            reconcileMediaListeners(activePartyIds)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing snapshot", e)
                        }
                    }
                }
            }
    }

    private fun stopSyncing() {
        Log.d(TAG, "Stopping real-time Firestore sync")
        partyListener?.remove()
        partyListener = null
        mediaListeners.values.forEach { it.remove() }
        mediaListeners.clear()
        isSyncing = false
    }

    private fun reconcileMediaListeners(activePartyIds: Set<String>) {
        val stalePartyIds = mediaListeners.keys - activePartyIds
        stalePartyIds.forEach { stopMediaListener(it) }
        activePartyIds.forEach { partyId ->
            if (partyId !in mediaListeners) {
                startMediaListener(partyId)
            }
        }
    }

    private fun startMediaListener(partyId: String) {
        mediaListeners[partyId] = firestore.collection("parties")
            .document(partyId)
            .collection("media")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Media listener error for $partyId: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                repositoryScope.launch {
                    try {
                        val toInsert = mutableListOf<MediaEntity>()
                        for (change in snapshot.documentChanges) {
                            val doc = change.document
                            val mediaId = doc.id
                            when (change.type) {
                                DocumentChange.Type.REMOVED -> {
                                    mediaDao.getMediaForPartyOnce(partyId)
                                        .firstOrNull { it.id == mediaId }
                                        ?.let { existing ->
                                            val file = java.io.File(existing.encryptedFilePath)
                                            if (file.exists()) {
                                                file.delete()
                                            }
                                        }
                                    mediaDao.deleteById(mediaId)
                                }
                                else -> {
                                    val createdAtMs = doc.getTimestamp("createdAt")?.toDate()?.time
                                    val flagged = doc.getBoolean("flagged") ?: false
                                    toInsert.add(
                                        MediaEntityFactory.fromRemote(
                                            mediaId = mediaId,
                                            partyId = partyId,
                                            filesDir = context.filesDir,
                                            createdAtEpochMs = createdAtMs,
                                            flagged = flagged,
                                            nowEpochMs = Clock.System.now().toEpochMilliseconds()
                                        )
                                    )
                                }
                            }
                        }
                        if (toInsert.isNotEmpty()) {
                            mediaDao.insertAll(toInsert)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing media snapshot for $partyId", e)
                    }
                }
            }
    }

    private fun stopMediaListener(partyId: String) {
        mediaListeners.remove(partyId)?.remove()
    }

    private suspend fun purgeLocalPartyAccess(partyId: String) {
        stopMediaListener(partyId)
        expirationScheduler.cancelExpiration(partyId)
        partyKeyStore.deleteKey(partyId)
        MediaFilePaths.deletePartyFiles(context.filesDir, partyId)
        mediaDao.deleteByPartyId(partyId)
        partyDao.softDelete(partyId)
    }

    private suspend fun ensureSharedMediaKey(
        partyId: String,
        hostUserId: String,
        existingKey: String?
    ) {
        if (!existingKey.isNullOrBlank()) {
            runCatching { partyKeyStore.importEncodedKey(partyId, existingKey) }
                .onFailure { Log.e(TAG, "Invalid media key for party $partyId") }
            return
        }

        val currentUserId = auth.currentUser?.uid ?: return
        if (currentUserId != hostUserId) {
            return
        }

        val generated = PartyKeyCodec.generate()
        val encoded = PartyKeyCodec.encode(generated)
        val partyRef = firestore.collection("parties").document(partyId)
        try {
            val stored = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(partyRef)
                val current = snapshot.getString("mediaKey")
                if (current.isNullOrBlank()) {
                    transaction.update(partyRef, "mediaKey", encoded)
                    encoded
                } else {
                    current
                }
            }.await()
            partyKeyStore.importEncodedKey(partyId, stored)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist shared media key for $partyId", e)
        } finally {
            generated.fill(0)
        }
    }

    private suspend fun importMediaKeyFromParty(partyId: String) {
        val partyDoc = firestore.collection("parties").document(partyId).get().await()
        val mediaKey = partyDoc.getString("mediaKey")
        val hostUserId = partyDoc.getString("hostUserId") ?: ""
        ensureSharedMediaKey(partyId, hostUserId, mediaKey)
    }

    override suspend fun createParty(name: String, expiresAt: Instant): Result<PartyEntity> {
        return try {
            val result = partyService.createParty(name, expiresAt.toEpochMilliseconds())
            result.fold(
                onSuccess = { data ->
                    @Suppress("UNCHECKED_CAST")
                    val partyMap = data["party"] as Map<String, Any>
                    val id = partyMap["id"] as String
                    val partyName = partyMap["name"] as String
                    val hostUserId = partyMap["hostUserId"] as String
                    val createdAtStr = partyMap["createdAt"] as String
                    val expiresAtStr = partyMap["expiresAt"] as String

                    val createdAt = Instant.parse(createdAtStr)
                    val expiresAtParsed = Instant.parse(expiresAtStr)

                    val partyEntity = PartyEntity(
                        id = id,
                        name = partyName,
                        hostUserId = hostUserId,
                        createdAt = createdAt,
                        expiresAt = expiresAtParsed,
                        isDeleted = false
                    )

                    partyDao.insert(partyEntity)
                    expirationScheduler.scheduleExpiration(id, expiresAtParsed)
                    importMediaKeyFromParty(id)

                    Result.success(partyEntity)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinParty(partyId: String): Result<PartyEntity> {
        return try {
            val result = partyService.joinParty(partyId)
            result.fold(
                onSuccess = { data ->
                    @Suppress("UNCHECKED_CAST")
                    val partyMap = data["party"] as Map<String, Any>
                    val id = partyMap["id"] as String
                    val partyName = partyMap["name"] as String
                    val hostUserId = partyMap["hostUserId"] as String
                    val createdAtStr = partyMap["createdAt"] as String
                    val expiresAtStr = partyMap["expiresAt"] as String

                    val createdAt = Instant.parse(createdAtStr)
                    val expiresAtParsed = Instant.parse(expiresAtStr)

                    val partyEntity = PartyEntity(
                        id = id,
                        name = partyName,
                        hostUserId = hostUserId,
                        createdAt = createdAt,
                        expiresAt = expiresAtParsed,
                        isDeleted = false
                    )

                    partyDao.insert(partyEntity)
                    expirationScheduler.scheduleExpiration(id, expiresAtParsed)
                    importMediaKeyFromParty(id)

                    Result.success(partyEntity)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun fetchUserParties(): Flow<List<PartyEntity>> {
        return partyDao.getActiveParties(Clock.System.now())
    }

    override suspend fun deleteParty(partyId: String): Result<Unit> {
        return try {
            purgeLocalPartyAccess(partyId)
            // Host deactivation happens in leaveParty (Admin SDK). A follow-up
            // client get/update would fail once the host is no longer a member.
            partyService.leaveParty(partyId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getPartyById(partyId: String): Flow<PartyEntity?> {
        return partyDao.getPartyById(partyId)
    }
}
