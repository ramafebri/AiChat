package com.rama.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.rama.aichat.navigation.GemmaChatRoute
import com.rama.aichat.navigation.HomeRoute
import com.rama.aichat.navigation.LiveCameraAnalyzerRoute
import com.rama.aichat.navigation.SkillEditRoute
import com.rama.aichat.navigation.SkillListRoute
import com.rama.aichat.ui.camera.LiveCameraAnalyzerScreen
import com.rama.aichat.ui.chat.ChatScreen
import com.rama.aichat.ui.chat.ChatScreenV2
import com.rama.aichat.ui.chat.ChatViewModel
import com.rama.aichat.ui.components.AppDrawer
import com.rama.aichat.ui.components.AppNavDestination
import com.rama.aichat.ui.skill.SkillEditScreen
import com.rama.aichat.ui.skill.SkillListScreen
import com.rama.aichat.ui.theme.AiChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiChatTheme {
                val backStack = rememberNavBackStack(HomeRoute)
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val chatViewModel: ChatViewModel = viewModel()
                val chatUiState by chatViewModel.uiState.collectAsState()

                val currentRoute = backStack.lastOrNull()
                val selectedDestination = when (currentRoute) {
                    is SkillListRoute, is SkillEditRoute -> AppNavDestination.Skills
                    is GemmaChatRoute -> AppNavDestination.GemmaChat
                    else -> AppNavDestination.Chat
                }

                val openDrawer: () -> Unit = {
                    scope.launch { drawerState.open() }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            AppDrawer(
                                selectedDestination = selectedDestination,
                                sessions = chatUiState.sessions,
                                currentSessionId = chatUiState.currentSessionId,
                                onNavigateToChat = {
                                    backStack.clear()
                                    backStack.add(HomeRoute)
                                    scope.launch { drawerState.close() }
                                },
                                onNavigateToGemmaChat = {
                                    backStack.clear()
                                    backStack.add(GemmaChatRoute)
                                    scope.launch { drawerState.close() }
                                },
                                onNavigateToSkills = {
                                    backStack.clear()
                                    backStack.add(SkillListRoute)
                                    scope.launch { drawerState.close() }
                                },
                                onSessionClick = { sessionId ->
                                    backStack.clear()
                                    backStack.add(HomeRoute)
                                    chatViewModel.selectSession(sessionId)
                                    scope.launch { drawerState.close() }
                                },
                                onNewChat = {
                                    backStack.clear()
                                    backStack.add(HomeRoute)
                                    chatViewModel.createNewSession()
                                    scope.launch { drawerState.close() }
                                },
                                onDeleteSession = chatViewModel::deleteSession
                            )
                        }
                    }
                ) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        entryProvider = entryProvider {
                            entry<HomeRoute> {
                                ChatScreen(onOpenDrawer = openDrawer)
                            }
                            entry<GemmaChatRoute> {
                                ChatScreenV2(
                                    onOpenDrawer = openDrawer,
                                    onOpenLiveAnalyzer = {
                                        backStack.add(LiveCameraAnalyzerRoute)
                                    }
                                )
                            }
                            entry<LiveCameraAnalyzerRoute> {
                                LiveCameraAnalyzerScreen(
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<SkillListRoute> {
                                SkillListScreen(
                                    onOpenDrawer = openDrawer,
                                    onCreateSkill = {
                                        backStack.add(SkillEditRoute(fileName = null))
                                    },
                                    onEditSkill = { fileName ->
                                        backStack.add(SkillEditRoute(fileName = fileName))
                                    }
                                )
                            }
                            entry<SkillEditRoute> { route ->
                                SkillEditScreen(
                                    fileName = route.fileName,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
