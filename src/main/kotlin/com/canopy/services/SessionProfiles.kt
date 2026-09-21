package com.canopy.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "CanopySessionProfiles",
    storages = [Storage("canopy.xml")]
)
@Service(Service.Level.APP)
class SessionProfiles : PersistentStateComponent<SessionProfiles.State> {

    class State {
        @JvmField var profileBySession: MutableMap<String, String> = LinkedHashMap()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun of(sessionKey: String): String? = myState.profileBySession[sessionKey]

    fun remember(sessionKey: String, profileName: String?) {
        if (profileName == null) myState.profileBySession.remove(sessionKey)
        else myState.profileBySession[sessionKey] = profileName
    }

    /** A session gets a new id every time it resumes, and the profile has to follow it there. */
    fun rekey(from: String, to: String) {
        if (from == to) return

        myState.profileBySession.remove(from)?.let { myState.profileBySession[to] = it }
    }

    fun forget(sessionKey: String) {
        myState.profileBySession.remove(sessionKey)
    }

    companion object {
        fun getInstance(): SessionProfiles =
            ApplicationManager.getApplication().getService(SessionProfiles::class.java)
    }
}
