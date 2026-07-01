package com.devexplorer.data.workspace

import android.content.Context
import com.devexplorer.core.capability.WorkspaceRepository
import com.devexplorer.core.capability.WriteCapability

/**
 * The mint for write tokens.
 *
 * [WorkspaceWriteToken] is `internal`, so it can be constructed ONLY inside this
 * module. That's the linchpin of the safety model: no other module can produce a
 * [WriteCapability], therefore no other module can build a write use-case. This
 * factory is the single, auditable place a token comes into existence.
 *
 * (Milestone 3 uses this manual factory; Hilt provides the same instances via a
 * module later, but the guarantee — internal constructor — is unchanged.)
 */
object WorkspaceModule {

    fun repository(context: Context): WorkspaceRepository =
        WorkspaceFileRepository(context)

    fun writeCapability(): WriteCapability = WorkspaceWriteToken()
}

/** The one concrete write capability. Internal on purpose — see above. */
internal class WorkspaceWriteToken : WriteCapability
