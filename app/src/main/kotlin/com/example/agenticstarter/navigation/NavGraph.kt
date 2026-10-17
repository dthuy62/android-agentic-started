package com.example.agenticstarter.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.agenticstarter.ui.chat.ChatRoute
import com.example.agenticstarter.ui.chat.ChatScreen
import com.example.agenticstarter.ui.settings.SettingsRoute
import com.example.agenticstarter.ui.settings.SettingsScreen
import java.util.Map.entry

/**
 * The only place that knows about every feature. Features expose a Route and a Screen;
 * they never reference each other, they only receive callbacks like `onOpenSettings`.
 */
@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(ChatRoute)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Order matters: saved-state holder first, then the ViewModel store. Without the second one,
        // hiltViewModel() inside an entry would be scoped to the Activity and never cleared on pop.
        entryDecorators =
        listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider =
        entryProvider {
            entry<ChatRoute> { ChatScreen(onOpenSettings = { backStack.add(SettingsRoute) }) }
            entry<SettingsRoute> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
