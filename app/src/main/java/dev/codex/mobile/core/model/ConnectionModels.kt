package dev.codex.mobile.core.model

enum class ConnectionPhase {
    Idle,
    Connecting,
    Connected,
    Disconnected,
    Error,
}

data class ConnectionState(
    val activeHostId: String? = null,
    val phase: ConnectionPhase = ConnectionPhase.Idle,
    val message: String? = null,
)

enum class AccountStatus {
    Unknown,
    RequiresLogin,
    ApiKey,
    ChatGpt,
}

data class AccountState(
    val status: AccountStatus = AccountStatus.Unknown,
    val email: String? = null,
    val planType: String? = null,
    val requiresOpenaiAuth: Boolean = false,
)

val ConnectionState.isConnected: Boolean
    get() = phase == ConnectionPhase.Connected

val AccountState.summary: String
    get() = when (status) {
        AccountStatus.Unknown -> "Account status unavailable"
        AccountStatus.RequiresLogin -> "Desktop needs Codex login"
        AccountStatus.ApiKey -> "Desktop is using an API key"
        AccountStatus.ChatGpt -> {
            val plan = planType?.replaceFirstChar(Char::titlecase) ?: "ChatGPT"
            if (email.isNullOrBlank()) {
                "$plan account"
            } else {
                "$plan account • $email"
            }
        }
    }
