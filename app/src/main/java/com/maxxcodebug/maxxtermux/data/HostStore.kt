package com.maxxcodebug.maxxtermux.data

import android.content.Context
import com.maxxcodebug.maxxtermux.model.AuthType
import com.maxxcodebug.maxxtermux.model.Host
import com.maxxcodebug.maxxtermux.model.HostKind
import com.maxxcodebug.maxxtermux.model.Snippet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Plain JSON files in app storage. Passwords and keys are already encrypted by SecretBox. */
class HostStore(private val ctx: Context) {

    private fun read(name: String): String? {
        val f = File(ctx.filesDir, name)
        return if (f.exists()) f.readText() else null
    }

    private fun write(name: String, text: String) {
        val f = File(ctx.filesDir, name)
        val tmp = File(ctx.filesDir, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.writeText(text)
            tmp.delete()
        }
    }

    // ---- settings (name, progress style, ...) ----
    fun loadSettings(): JSONObject {
        val txt = read("settings.json") ?: return JSONObject()
        return try {
            JSONObject(txt)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun saveSettings(o: JSONObject) {
        write("settings.json", o.toString())
    }

    // ---- hosts ----
    fun loadHosts(): List<Host> {
        val txt = read("hosts.json") ?: return emptyList()
        return try {
            val arr = JSONArray(txt)
            val out = ArrayList<Host>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Host(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        user = o.getString("user"),
                        address = o.getString("address"),
                        port = o.optInt("port", 22),
                        kind = HostKind.valueOf(o.optString("kind", "Server")),
                        authType = AuthType.valueOf(o.optString("auth", "Password")),
                        secretEnc = o.optString("secret", ""),
                        passphraseEnc = o.optString("passphrase", ""),
                    ),
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHosts(list: List<Host>) {
        val arr = JSONArray()
        for (h in list) {
            val o = JSONObject()
            o.put("id", h.id)
            o.put("name", h.name)
            o.put("user", h.user)
            o.put("address", h.address)
            o.put("port", h.port)
            o.put("kind", h.kind.name)
            o.put("auth", h.authType.name)
            o.put("secret", h.secretEnc)
            o.put("passphrase", h.passphraseEnc)
            arr.put(o)
        }
        write("hosts.json", arr.toString())
    }

    // ---- snippets ----
    fun loadSnippets(): List<Snippet> {
        val txt = read("snippets.json") ?: return defaultSnippets()
        return try {
            val arr = JSONArray(txt)
            val out = ArrayList<Snippet>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Snippet(o.getString("id"), o.getString("group"), o.getString("text")))
            }
            out
        } catch (e: Exception) {
            defaultSnippets()
        }
    }

    fun saveSnippets(list: List<Snippet>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("group", s.group)
            o.put("text", s.text)
            arr.put(o)
        }
        write("snippets.json", arr.toString())
    }

    private fun defaultSnippets(): List<Snippet> {
        fun s(group: String, text: String) = Snippet(UUID.randomUUID().toString(), group, text)
        return listOf(
            s("AOSP build", ". build/envsetup.sh"),
            s("AOSP build", "lunch"),
            s("AOSP build", "m"),
            s("AOSP build", "mka bacon"),
            s("AOSP build", "repo sync"),
            s("Git", "git add ."),
            s("Git", "git cherry-pick --continue"),
            s("GitHub CLI", "gh auth login"),
            s("GitHub CLI", "gh auth setup-git"),
            s("Navigate", "cd .."),
        )
    }

    // ---- known host fingerprints (trust on first use) ----
    fun loadKnown(): Map<String, String> {
        val txt = read("known_hosts.json") ?: return emptyMap()
        return try {
            val o = JSONObject(txt)
            val out = HashMap<String, String>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = o.getString(k)
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveKnown(map: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        write("known_hosts.json", o.toString())
    }
}
