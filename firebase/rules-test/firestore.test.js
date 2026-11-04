// Run with: cd firebase/rules-test && npm ci && npm test   (needs Java for the Firestore emulator).
// Not part of the default Gradle or backend test runs. Written against firebase/firestore.rules; if the rules change, run this.
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { Timestamp, collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

let env;
const CODE = "ABCD2345";
const minutes = (m) => Timestamp.fromMillis(Date.now() + m * 60_000);

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-carecircle",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 },
  });
});
afterAll(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const parent = () => env.authenticatedContext("parent").firestore();
const carer = () => env.authenticatedContext("carer").firestore();
const stranger = () => env.authenticatedContext("stranger").firestore();
const anon = () => env.unauthenticatedContext().firestore();

const pairing = (extra = {}) => ({ ownerUid: "parent", personName: "", createdAt: Timestamp.now(), expiresAt: minutes(15), caregiverUid: null, ...extra });
const makePairing = (db = parent(), extra = {}) => setDoc(doc(db, "pairings", CODE), pairing(extra));
const seed = (data, path = ["pairings", CODE]) => env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), ...path), data));
const event = (extra = {}) => ({ type: "taken", person: "", medicine: "8:00 AM", scheduledAt: 1, at: 2, taken: 0, total: 0, ...extra });

describe("pairings", () => {
  it("the parent creates an unclaimed, short-lived one", async () => { await assertSucceeds(makePairing()); });
  it("signed-out users cannot", async () => { await assertFails(makePairing(anon())); });
  it("it cannot name another owner or start with a caregiver set", async () => {
    await assertFails(makePairing(parent(), { ownerUid: "someone-else" }));
    await assertFails(makePairing(parent(), { caregiverUid: "carer" }));
  });
  it("it cannot last longer than 20 minutes or carry extra fields", async () => {
    await assertFails(makePairing(parent(), { expiresAt: minutes(60) }));
    await assertFails(makePairing(parent(), { extra: "x" }));
  });
  it("nobody can list pairings", async () => {
    await seed(pairing());
    await assertFails(getDocs(collection(carer(), "pairings")));
  });
  it("any signed-in user can look up one by its code, but only while it is free", async () => {
    await seed(pairing());
    await assertSucceeds(getDoc(doc(stranger(), "pairings", CODE)));
    await seed(pairing({ caregiverUid: "carer" }));
    await assertFails(getDoc(doc(stranger(), "pairings", CODE)));
    await assertSucceeds(getDoc(doc(carer(), "pairings", CODE)));
    await assertSucceeds(getDoc(doc(parent(), "pairings", CODE)));
  });
});

describe("claiming", () => {
  it("a caregiver claims a free, unexpired pairing", async () => {
    await seed(pairing());
    await assertSucceeds(updateDoc(doc(carer(), "pairings", CODE), { caregiverUid: "carer", linkedAt: Timestamp.now() }));
  });
  it("only once: a second caregiver is refused", async () => {
    await seed(pairing({ caregiverUid: "carer" }));
    await assertFails(updateDoc(doc(stranger(), "pairings", CODE), { caregiverUid: "stranger", linkedAt: Timestamp.now() }));
  });
  it("not after it has expired", async () => {
    await seed(pairing({ expiresAt: Timestamp.fromMillis(Date.now() - 1000) }));
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverUid: "carer", linkedAt: Timestamp.now() }));
  });
  it("claiming cannot change anything else, or name someone else", async () => {
    await seed(pairing());
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverUid: "carer", ownerUid: "carer" }));
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverUid: "stranger" }));
  });
  it("either side can end it, a stranger cannot", async () => {
    await seed(pairing({ caregiverUid: "carer" }));
    await assertFails(deleteDoc(doc(stranger(), "pairings", CODE)));
    await assertSucceeds(deleteDoc(doc(carer(), "pairings", CODE)));
  });
});

describe("several caregivers for one parent", () => {
  const CODE2 = "WXYZ6789";
  it("each link has its own caregiver, events and token, and none can read the others", async () => {
    await seed(pairing({ caregiverUid: "carer" }));
    await seed(pairing({ caregiverUid: "second" }), ["pairings", CODE2]);
    await seed(event(), ["pairings", CODE, "events", "taken-1"]);
    await seed(event(), ["pairings", CODE2, "events", "taken-1"]);
    const second = env.authenticatedContext("second").firestore();
    await assertSucceeds(getDoc(doc(carer(), "pairings", CODE, "events", "taken-1")));
    await assertFails(getDoc(doc(carer(), "pairings", CODE2, "events", "taken-1")));
    await assertSucceeds(getDoc(doc(second, "pairings", CODE2, "events", "taken-1")));
    await assertFails(getDoc(doc(second, "pairings", CODE, "events", "taken-1")));
    await assertSucceeds(updateDoc(doc(second, "pairings", CODE2), { caregiverFcmToken: "tok-2" }));
    await assertFails(updateDoc(doc(second, "pairings", CODE), { caregiverFcmToken: "tok-2" }));
  });
  it("removing one link leaves the other working", async () => {
    await seed(pairing({ caregiverUid: "carer" }));
    await seed(pairing({ caregiverUid: "second" }), ["pairings", CODE2]);
    await seed(event(), ["pairings", CODE2, "events", "taken-1"]);
    await assertSucceeds(deleteDoc(doc(parent(), "pairings", CODE)));
    const second = env.authenticatedContext("second").firestore();
    await assertSucceeds(getDoc(doc(second, "pairings", CODE2, "events", "taken-1")));
  });
});

describe("push token", () => {
  beforeEach(() => seed(pairing({ caregiverUid: "carer" })));
  it("the family phone writes only its token and the time on a link it claimed", async () => {
    await assertSucceeds(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: "tok", tokenUpdatedAt: Timestamp.now() }));
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: "tok", ownerUid: "carer" }));
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: 123 }));
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: "x".repeat(500) }));
  });
  it("nobody else can write it", async () => {
    await assertFails(updateDoc(doc(parent(), "pairings", CODE), { caregiverFcmToken: "tok" }));
    await assertFails(updateDoc(doc(stranger(), "pairings", CODE), { caregiverFcmToken: "tok" }));
  });
  it("the family phone can remove it again", async () => {
    await seed(pairing({ caregiverUid: "carer", caregiverFcmToken: "tok", tokenUpdatedAt: Timestamp.now() }));
    const { deleteField } = await import("firebase/firestore");
    await assertSucceeds(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: deleteField(), tokenUpdatedAt: deleteField() }));
  });
  it("a revoked caregiver (link deleted) cannot write a token", async () => {
    await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), "pairings", CODE)));
    await assertFails(updateDoc(doc(carer(), "pairings", CODE), { caregiverFcmToken: "tok" }));
  });
});

describe("events and status", () => {
  beforeEach(() => seed(pairing({ caregiverUid: "carer" })));

  it("only the parent writes events, with the allowed fields only", async () => {
    await assertSucceeds(setDoc(doc(parent(), "pairings", CODE, "events", "taken-1"), event()));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "events", "taken-2"), event()));
    await assertFails(setDoc(doc(parent(), "pairings", CODE, "events", "taken-3"), event({ note: "private" })));
    await assertFails(setDoc(doc(parent(), "pairings", CODE, "events", "taken-4"), event({ type: "bogus" })));
  });
  it("only the two phones on the pairing read them", async () => {
    await seed(event(), ["pairings", CODE, "events", "taken-1"]);
    await assertSucceeds(getDoc(doc(parent(), "pairings", CODE, "events", "taken-1")));
    await assertSucceeds(getDoc(doc(carer(), "pairings", CODE, "events", "taken-1")));
    await assertFails(getDoc(doc(stranger(), "pairings", CODE, "events", "taken-1")));
    await assertFails(getDoc(doc(anon(), "pairings", CODE, "events", "taken-1")));
  });
  it("status: the parent writes the allowed fields, both read, others do not", async () => {
    const status = { day: 1, taken: 1, total: 2, missed: 0, skipped: 0, nextAt: 0, nextMedicine: "", updatedAt: 5 };
    await assertSucceeds(setDoc(doc(parent(), "pairings", CODE, "status", "current"), status));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "status", "current"), status));
    await assertFails(setDoc(doc(parent(), "pairings", CODE, "status", "current"), { ...status, extra: 1 }));
    await assertSucceeds(getDoc(doc(carer(), "pairings", CODE, "status", "current")));
    await assertFails(getDoc(doc(stranger(), "pairings", CODE, "status", "current")));
  });
  it("once the pairing is deleted its events cannot be read", async () => {
    await seed(event(), ["pairings", CODE, "events", "taken-1"]);
    await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), "pairings", CODE)));
    await assertFails(getDoc(doc(carer(), "pairings", CODE, "events", "taken-1")));
  });
  it("the parent can delete old events, the caregiver cannot", async () => {
    await seed(event(), ["pairings", CODE, "events", "taken-1"]);
    await assertFails(deleteDoc(doc(carer(), "pairings", CODE, "events", "taken-1")));
    await assertSucceeds(deleteDoc(doc(parent(), "pairings", CODE, "events", "taken-1")));
  });
});

describe("acknowledgements", () => {
  const KEY = "2026-11-04_1793775600000";
  const ack = (extra = {}) => ({ at: serverTimestamp(), byRole: "caregiver", ...extra });
  beforeEach(() => seed(pairing({ caregiverUid: "carer" })));

  it("the caregiver creates one note per dose time", async () => {
    await assertSucceeds(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), ack()));
  });
  it("a second tap cannot change it (no update)", async () => {
    await assertSucceeds(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), ack()));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), ack()));
    await assertFails(updateDoc(doc(carer(), "pairings", CODE, "acks", KEY), { byRole: "caregiver" }));
  });
  it("only the claimed caregiver can create one: not the parent, a stranger or a revoked caregiver", async () => {
    await assertFails(setDoc(doc(parent(), "pairings", CODE, "acks", KEY), ack()));
    await assertFails(setDoc(doc(stranger(), "pairings", CODE, "acks", KEY), ack()));
    await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), "pairings", CODE)));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), ack()));
  });
  it("it must look exactly right: key shape, fields, role and the server time", async () => {
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "acks", "not-a-key"), ack()));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), ack({ note: "x" })));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), ack({ byRole: "parent" })));
    await assertFails(setDoc(doc(carer(), "pairings", CODE, "acks", KEY), ack({ at: Timestamp.fromMillis(5) })));
  });
  it("the parent reads and tidies them, the caregiver and strangers cannot read", async () => {
    await seed({ at: Timestamp.now(), byRole: "caregiver" }, ["pairings", CODE, "acks", KEY]);
    await assertSucceeds(getDoc(doc(parent(), "pairings", CODE, "acks", KEY)));
    await assertSucceeds(getDocs(collection(parent(), "pairings", CODE, "acks")));
    await assertFails(getDoc(doc(carer(), "pairings", CODE, "acks", KEY)));
    await assertFails(getDoc(doc(stranger(), "pairings", CODE, "acks", KEY)));
    await assertFails(deleteDoc(doc(carer(), "pairings", CODE, "acks", KEY)));
    await assertSucceeds(deleteDoc(doc(parent(), "pairings", CODE, "acks", KEY)));
  });
  it("the status can carry the handled list, up to 200, and nothing else new", async () => {
    const status = { day: 1, taken: 0, total: 1, missed: 1, skipped: 0, nextAt: 0, nextMedicine: "", updatedAt: 5, handledSlots: [KEY] };
    await assertSucceeds(setDoc(doc(parent(), "pairings", CODE, "status", "current"), status));
    await assertFails(setDoc(doc(parent(), "pairings", CODE, "status", "current"), { ...status, handledSlots: Array(201).fill(KEY) }));
    await assertFails(setDoc(doc(parent(), "pairings", CODE, "status", "current"), { ...status, handledSlots: "nope" }));
  });
});
