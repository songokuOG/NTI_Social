/**
 * Cloud Functions for NTISocial/Afterlight
 * Handles party creation, joining, and cleanup operations
 */

import {onCall} from "firebase-functions/v2/https";
import {onSchedule} from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";
import {setGlobalOptions} from "firebase-functions/v2";
import * as crypto from "crypto";

admin.initializeApp();

// Set global options for cost control
setGlobalOptions({maxInstances: 10});

/**
 * Create a new party
 */
export const createParty = onCall(async (request) => {
  if (!request.auth) {
    throw new Error("User must be authenticated");
  }

  const {name, expiresAt} = request.data;

  if (!name || typeof name !== "string") {
    throw new Error("Party name is required");
  }

  if (!expiresAt || typeof expiresAt !== "number") {
    throw new Error("Party expiration time is required");
  }

  const userId = request.auth.uid;
  const db = admin.firestore();

  try {
    const expiresDate = new Date(expiresAt);
    const mediaKey = crypto.randomBytes(32).toString("base64");
    const partyRef = await db.collection("parties").add({
      name: name,
      hostUserId: userId,
      members: [userId],
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      expiresAt: expiresDate,
      isActive: true,
      mediaKey: mediaKey,
    });

    logger.info(`Party created: ${partyRef.id} by user ${userId}`);

    return {
      success: true,
      party: {
        id: partyRef.id,
        name: name,
        hostUserId: userId,
        createdAt: new Date().toISOString(),
        expiresAt: expiresDate.toISOString(),
        isActive: true,
        members: [userId],
      },
    };
  } catch (error) {
    logger.error("Error creating party:", error);
    throw new Error("Failed to create party");
  }
});

/**
 * Join an existing party
 */
export const joinParty = onCall(async (request) => {
  if (!request.auth) {
    throw new Error("User must be authenticated");
  }

  const {partyId} = request.data;

  if (!partyId || typeof partyId !== "string") {
    throw new Error("Party ID is required");
  }

  const userId = request.auth.uid;
  const db = admin.firestore();

  try {
    const partyRef = db.collection("parties").doc(partyId);
    const partyDoc = await partyRef.get();

    if (!partyDoc.exists) {
      throw new Error("Party not found");
    }

    const partyData = partyDoc.data();

    if (!partyData?.isActive) {
      throw new Error("Party is no longer active");
    }

    const fallbackDate = new Date().toISOString();
    const createdStr = partyData.createdAt?.toDate().toISOString() ||
      fallbackDate;
    const expiresStr = partyData.expiresAt?.toDate().toISOString() ||
      fallbackDate;

    if (partyData?.members?.includes(userId)) {
      return {
        success: true,
        message: "Already a member",
        party: {
          id: partyDoc.id,
          name: partyData.name,
          hostUserId: partyData.hostUserId,
          createdAt: createdStr,
          expiresAt: expiresStr,
          isActive: partyData.isActive,
          members: partyData.members,
        },
      };
    }

    await partyRef.update({
      members: admin.firestore.FieldValue.arrayUnion(userId),
    });

    logger.info(`User ${userId} joined party ${partyId}`);

    const updatedPartyDoc = await partyRef.get();
    const updatedPartyData = updatedPartyDoc.data();

    const updCreatedStr = updatedPartyData?.createdAt?.toDate().toISOString() ||
      fallbackDate;
    const updExpiresStr = updatedPartyData?.expiresAt?.toDate().toISOString() ||
      fallbackDate;

    return {
      success: true,
      message: "Successfully joined party",
      party: {
        id: updatedPartyDoc.id,
        name: updatedPartyData?.name,
        hostUserId: updatedPartyData?.hostUserId,
        createdAt: updCreatedStr,
        expiresAt: updExpiresStr,
        isActive: updatedPartyData?.isActive,
        members: updatedPartyData?.members,
      },
    };
  } catch (error) {
    logger.error("Error joining party:", error);
    const err = error as Error;
    throw new Error(err.message || "Failed to join party");
  }
});

/**
 * Get details of a party.
 * Intentionally kept as a callable contract for future/non-Android clients.
 * The Android app reads party state from Firestore + Room instead.
 */
export const getParty = onCall(async (request) => {
  if (!request.auth) {
    throw new Error("User must be authenticated");
  }

  const {partyId} = request.data;

  if (!partyId || typeof partyId !== "string") {
    throw new Error("Party ID is required");
  }

  const userId = request.auth.uid;
  const db = admin.firestore();

  try {
    const partyDoc = await db.collection("parties").doc(partyId).get();

    if (!partyDoc.exists) {
      throw new Error("Party not found");
    }

    const partyData = partyDoc.data();

    if (!partyData?.members?.includes(userId)) {
      throw new Error("Permission denied: user is not a member of this party");
    }

    const fallbackDate = new Date().toISOString();
    const createdStr = partyData.createdAt?.toDate().toISOString() ||
      fallbackDate;
    const expiresStr = partyData.expiresAt?.toDate().toISOString() ||
      fallbackDate;

    return {
      success: true,
      party: {
        id: partyDoc.id,
        name: partyData.name,
        hostUserId: partyData.hostUserId,
        createdAt: createdStr,
        expiresAt: expiresStr,
        isActive: partyData.isActive,
        members: partyData.members,
      },
    };
  } catch (error) {
    logger.error("Error getting party:", error);
    const err = error as Error;
    throw new Error(err.message || "Failed to get party");
  }
});

/**
 * Leave a party
 */
export const leaveParty = onCall(async (request) => {
  if (!request.auth) {
    throw new Error("User must be authenticated");
  }

  const {partyId} = request.data;

  if (!partyId || typeof partyId !== "string") {
    throw new Error("Party ID is required");
  }

  const userId = request.auth.uid;
  const db = admin.firestore();

  try {
    const partyRef = db.collection("parties").doc(partyId);
    const partyDoc = await partyRef.get();

    if (!partyDoc.exists) {
      throw new Error("Party not found");
    }

    const partyData = partyDoc.data();

    if (!partyData?.members?.includes(userId)) {
      throw new Error("User is not a member of this party");
    }

    if (partyData.hostUserId === userId) {
      await partyRef.update({
        members: admin.firestore.FieldValue.arrayRemove(userId),
        isActive: false,
        deactivatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    } else {
      await partyRef.update({
        members: admin.firestore.FieldValue.arrayRemove(userId),
      });
    }

    logger.info(`User ${userId} left party ${partyId}`);

    return {
      success: true,
      message: "Successfully left party",
    };
  } catch (error) {
    logger.error("Error leaving party:", error);
    const err = error as Error;
    throw new Error(err.message || "Failed to leave party");
  }
});

/**
 * Get media list for a party with pagination.
 * Intentionally kept as a callable contract. Android uses a Firestore
 * media listener and does not call this function.
 */
export const getPartyMedia = onCall(async (request) => {
  if (!request.auth) {
    throw new Error("User must be authenticated");
  }

  const {partyId, limit = 20, startAfter} = request.data;

  if (!partyId || typeof partyId !== "string") {
    throw new Error("Party ID is required");
  }

  const userId = request.auth.uid;
  const db = admin.firestore();

  try {
    const partyDoc = await db.collection("parties").doc(partyId).get();

    if (!partyDoc.exists || !partyDoc.data()?.members?.includes(userId)) {
      throw new Error("Permission denied or party not found");
    }

    let query = db.collection("parties").doc(partyId).collection("media")
      .orderBy("createdAt", "desc")
      .limit(limit);

    if (startAfter) {
      const startAfterDoc = await db.collection("parties").doc(partyId)
        .collection("media").doc(startAfter).get();
      if (startAfterDoc.exists) {
        query = query.startAfter(startAfterDoc);
      }
    }

    const mediaSnap = await query.get();
    const fallbackDate = new Date().toISOString();
    const mediaList = mediaSnap.docs.map((doc) => ({
      id: doc.id,
      partyId: partyId,
      createdAt: doc.data().createdAt?.toDate().toISOString() || fallbackDate,
      flagged: doc.data().flagged || false,
    }));

    const lastId = mediaSnap.docs.length > 0 ?
      mediaSnap.docs[mediaSnap.docs.length - 1].id : null;

    return {
      success: true,
      media: mediaList,
      lastId: lastId,
    };
  } catch (error) {
    logger.error("Error getting party media:", error);
    const err = error as Error;
    throw new Error(err.message || "Failed to get party media");
  }
});

/**
 * Clean up expired parties hourly
 */
export const cleanupExpiredParties = onSchedule("every 1 hours", async () => {
  const db = admin.firestore();
  const now = admin.firestore.Timestamp.now();

  try {
    const expiredPartiesSnapshot = await db.collection("parties")
      .where("expiresAt", "<=", now.toDate())
      .where("isActive", "==", true)
      .get();

    const batch = db.batch();

    expiredPartiesSnapshot.forEach((doc) => {
      batch.update(doc.ref, {
        isActive: false,
        deactivatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });

    await batch.commit();

    logger.info(`Deactivated ${expiredPartiesSnapshot.size} expired parties`);
  } catch (error) {
    logger.error("Error cleaning up expired parties:", error);
  }
});
