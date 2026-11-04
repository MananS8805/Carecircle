// The whole life of one pairing, using exactly the documents the Android app writes (see ContractTest in the app).
// Run with: cd firebase/rules-test && npm test   (needs Java for the Firestore emulator).
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { Timestamp, collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

let env;
const CODE = "UG4WT68K";
const OTHER = "ZWN4GHCJ";
const minutes = (m) => Timestamp.fromMillis(Date.now() + m * 60_000);

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-carecircle-flow",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 },
  });
});
afterAll(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const parent = () => env.authenticatedContext("parent").firestore();
const carer = () => env.authenticatedContext("carer").firestore();
const carer2 = () => env.authenticatedContext("carer2").firestore();
const stranger = () => env.authenticatedContext("stranger").firestore();

// Field for field what CareSync.createPairing / publish / publishStatus / writeAck send.
const newPairing = (over = {}) => ({ ownerUid: "parent", personName: "", createdAt: Timestamp.now(), expiresAt: minutes(15), caregiverUid: null, ...over });
const appStatus = (over = {}) => ({ day: 20_000, taken: 2, total: 3, missed: 1, skipped: 0, nextAt: 0, nextMedicine: "", updatedAt: Date.now(), handledSlots: [], ...over });
const appEvent = (over = {}) => ({ type: "taken", person: "", medicine: "8:00 am dose", scheduledAt: 1, at: 2, taken: 1, total: 1, ...over });
const KEY = "2026-11-04_1793759400000";
const link = (db = carer(), code = CODE) => updateDoc(doc(db, "pairings", code), { caregiverUid: db === carer() ? "carer" : "carer2", linkedAt: Timestamp.now() });
const seedPairing = (code = CODE, over = {}) => env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "pairings", code), newPairing(over)));

describe("the whole life of a pairing", () => {
  it("create, claim, send events and status, read them, register a push token, acknowledge, tidy up", async () => {
    await assertSucceeds(setDoc(doc(parent(), "pairings", CODE), newPairing()));
    await assertSucceeds(updateDoc(doc(carer(), "pairings", CODE), { caregiverUid: "carer", linkedAt: Timestamp.now() }));
    await assertSucceeds(setDoc(doc(parent(), "pairings", CODE, "events", "taken-1"), appEvent()));
    await assertSucceeds(setDoc(doc(parent(), "pairings", CODE, "status", "current"), appStatus()));
    await assertSucceeds(getDoc(doc(carer(), "pairings", CODE, "status", "current")));
    await assertSucceeds(getDocs(collection(carer(), "pairings", CODE, "events")));
    await assertSucceeds(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: "t".repeat(150), tokenUpdatedAt: Timestamp.now() }));
    await assertSucceeds(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), { at: serverTimestamp(), byRole: "caregiver" }));
    await assertSucceeds(getDoc(doc(parent(), "pairings", CODE, "acks", KEY)));
    await assertSucceeds(deleteDoc(doc(parent(), "pairings", CODE, "acks", KEY)));
  });

  it("every event type the app sends is accepted", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    for (const type of ["taken", "missed", "skipped", "summary"]) await assertSucceeds(setDoc(doc(parent(), "pairings", CODE, "events", type), appEvent({ type })));
    await assertFails(setDoc(doc(parent(), "pairings", CODE, "events", "x"), appEvent({ type: "deleted" })));
  });

  it("the status the app sends right after pairing, with an empty handled list, is accepted", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertSucceeds(setDoc(doc(parent(), "pairings", CODE, "status", "current"), appStatus({ total: 0, taken: 0, missed: 0 })));
  });

  it("writing the status to a pairing that no longer exists is refused (the symptom of a deleted link)", async () => {
    await assertFails(setDoc(doc(parent(), "pairings", CODE, "status", "current"), appStatus()));
  });

  it("reading the status of a pairing that no longer exists is refused, so a family phone sees 'no access'", async () => {
    await assertFails(getDoc(doc(carer(), "pairings", CODE, "status", "current")));
  });
});

describe("who may do what", () => {
  it("a second family phone cannot take over a claimed pairing", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertFails(updateDoc(doc(carer2(), "pairings", CODE), { caregiverUid: "carer2", linkedAt: Timestamp.now() }));
  });

  it("an expired code cannot be claimed", async () => {
    await seedPairing(CODE, { expiresAt: Timestamp.fromMillis(Date.now() - 1000) });
    await assertFails(link());
  });

  it("claiming cannot change anything but the caregiver and the time", async () => {
    await seedPairing();
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverUid: "carer", linkedAt: Timestamp.now(), ownerUid: "carer" }));
  });

  it("the family phone cannot write events, status or read acknowledgements", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "events", "e"), appEvent()));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "status", "current"), appStatus()));
    await env.withSecurityRulesDisabled((c) => setDoc(doc(c.firestore(), "pairings", CODE, "acks", KEY), { at: Timestamp.now(), byRole: "caregiver" }));
    await assertFails(getDoc(doc(carer(), "pairings", CODE, "acks", KEY)));
  });

  it("a stranger can read and write nothing inside a claimed pairing", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertFails(getDoc(doc(stranger(), "pairings", CODE, "status", "current")));
    await assertFails(getDocs(collection(stranger(), "pairings", CODE, "events")));
    await assertFails(setDoc(doc(stranger(), "pairings", CODE, "status", "current"), appStatus()));
    await assertFails(setDoc(doc(stranger(), "pairings", CODE, "events", "e"), appEvent()));
  });

  it("nobody can list the pairings", async () => {
    await seedPairing();
    await assertFails(getDocs(collection(parent(), "pairings")));
    await assertFails(getDocs(collection(stranger(), "pairings")));
  });

  it("two pairings of the same parent are independent", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await seedPairing(OTHER, { caregiverUid: "carer2" });
    await assertSucceeds(setDoc(doc(parent(), "pairings", CODE, "status", "current"), appStatus()));
    await assertSucceeds(setDoc(doc(parent(), "pairings", OTHER, "status", "current"), appStatus({ taken: 0 })));
    await assertSucceeds(getDoc(doc(carer(), "pairings", CODE, "status", "current")));
    await assertFails(getDoc(doc(carer(), "pairings", OTHER, "status", "current")));
    await assertFails(getDoc(doc(carer2(), "pairings", CODE, "status", "current")));
  });

  it("either side can end the pairing, and after that nobody can read it", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertSucceeds(deleteDoc(doc(carer(), "pairings", CODE)));
    await assertFails(getDoc(doc(carer(), "pairings", CODE, "status", "current")));
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertSucceeds(deleteDoc(doc(parent(), "pairings", CODE)));
  });

  it("a stranger cannot end somebody else's pairing", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertFails(deleteDoc(doc(stranger(), "pairings", CODE)));
  });

  it("a push token must be a short string", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: "t".repeat(500), tokenUpdatedAt: Timestamp.now() }));
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: 5, tokenUpdatedAt: Timestamp.now() }));
  });

  it("an acknowledgement key must look like day_millis and cannot be rewritten", async () => {
    await seedPairing(CODE, { caregiverUid: "carer" });
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "acks", "not-a-key"), { at: serverTimestamp(), byRole: "caregiver" }));
    await assertSucceeds(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), { at: serverTimestamp(), byRole: "caregiver" }));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), { at: serverTimestamp(), byRole: "caregiver" }));
  });
});
