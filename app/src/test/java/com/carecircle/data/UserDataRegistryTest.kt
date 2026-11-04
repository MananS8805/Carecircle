package com.carecircle.data

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataRegistryTest {
    private val module = File(System.getProperty("user.dir") ?: ".")
    private val app = if (File(module, "schemas").exists()) module else File(module, "app")

    private fun latestSchemaTables(): Set<String> {
        val dir = File(app, "schemas").listFiles { f -> f.isDirectory }!!.single()
        val newest = dir.listFiles { f -> f.name.endsWith(".json") }!!.maxByOrNull { it.nameWithoutExtension.toInt() }!!
        val entities = Json.parseToJsonElement(newest.readText()).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        return entities.map { it.jsonObject["tableName"]!!.jsonPrimitive.content }.toSet()
    }

    @Test fun everyRoomTableIsRegistered() {
        val registered = UserDataRegistry.of(StoreKind.ROOM_TABLE).map { it.id }.toSet()
        val missing = latestSchemaTables() - registered
        assertTrue("Room tables missing from UserDataRegistry: $missing", missing.isEmpty())
        val stale = registered - latestSchemaTables()
        assertTrue("Registry lists tables that no longer exist: $stale", stale.isEmpty())
    }

    @Test fun everyPrefsKeyIsRegistered() {
        val src = File(app, "src/main/java/com/carecircle/data/Prefs.kt").readText()
        val keys = Regex("""sp\.(?:getString|getBoolean|getInt|getLong|putString|putBoolean|putInt|putLong)\("([A-Za-z]+)"""").findAll(src).map { it.groupValues[1] }.toSet()
        assertTrue(keys.isNotEmpty())
        val registered = UserDataRegistry.of(StoreKind.PREFS_KEY).map { it.id }.toSet()
        assertTrue("Prefs keys missing from UserDataRegistry: ${keys - registered}", (keys - registered).isEmpty())
    }

    @Test fun everyExportedFieldExistsInTheBackupFileAndViceVersa() {
        val fields = BackupFile.serializer().descriptor.let { d -> (0 until d.elementsCount).map { d.getElementName(it) } }.toSet()
        val exported = UserDataRegistry.exportedFields()
        assertTrue("registry fields not in BackupFile: ${exported - fields}", (exported - fields).isEmpty())
        val carriers = fields - setOf("app", "schemaVersion", "exportedAt", "settings") // settings is the pre-version-3 contact block
        assertEquals("BackupFile fields no registry store claims", emptySet<String>(), carriers - exported)
    }

    @Test fun cachesAndSyncAreWipedButNeverExportedOrRestored() {
        for (id in listOf("info_card", "interaction_cache", "sync_link", "following")) {
            val s = UserDataRegistry.stores.single { it.id == id }
            assertTrue(s.wipeOnDelete); assertTrue(!s.exportable); assertTrue(!s.restorable)
        }
    }

    @Test fun displaySettingsAndLanguageSurviveDeleteAll() {
        for (k in listOf("language", "textSize", "highContrast", "simpleMode")) assertTrue(!UserDataRegistry.stores.single { it.id == k }.wipeOnDelete)
        for (k in listOf("appMode", "guideDone", "rescheduleLog", "selectedPerson")) assertTrue(UserDataRegistry.stores.single { it.id == k }.wipeOnDelete)
    }

    @Test fun runtimeStoresArePresent() {
        for (k in StoreKind.entries) assertTrue("no store of kind $k", UserDataRegistry.of(k).isNotEmpty())
    }

    @Test fun idsAreUnique() {
        val ids = UserDataRegistry.stores.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
