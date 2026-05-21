# Ollama Mobile — Claude Code Guide

Android chat app (Java/Kotlin, MVVM, Data Binding) that connects to a local or cloud Ollama server.

## Project layout

```
app/src/main/java/com/ollama/mobile/
  ui/
    chat/           ChatActivity (DrawerLayout + sidebar), ChatViewModel, ChatMessageAdapter
    conversations/  ConversationListActivity (DrawerLayout + NavigationView), ConversationListViewModel
    interview/      InterviewSetupActivity, InterviewActivity, InterviewViewModel
    settings/       SettingsActivity, SettingsViewModel
    model/          ModelLibraryActivity, ModelSelectorFragment
    search/         SearchActivity
  repository/       ChatRepository, ConversationRepository, SettingsRepository, ModelRepository, InterviewRepository
  network/          OllamaClient, OllamaApiService, HealthChecker, LocalNetworkScanner
  data/db/          AppDatabase (Room), ConversationDao, MessageDao
  model/            ChatRequest, ChatChunk, OllamaModel, InterviewRole
  attachment/       AttachmentPicker, FileContentExtractor

app/src/main/res/
  layout/           activity_*.xml, fragment_*.xml, item_*.xml, layout_sidebar.xml
  menu/             nav_drawer_menu.xml  (Conversations, Interview, Models, Settings)
  values/           colors.xml, strings.xml, themes.xml
```

## Navigation pattern

All main activities use `DrawerLayout` + `NavigationView` with `nav_drawer_menu.xml`.  
`ActionBarDrawerToggle` provides the hamburger icon.  Activities implement `NavigationView.OnNavigationItemSelectedListener`.

- `ConversationListActivity` — `nav_conversations` (root screen)
- `InterviewSetupActivity` — `nav_interview`
- `InterviewActivity` — `nav_interview` (active session)
- `SettingsActivity` — `nav_settings`

## Window insets

- Activities whose root is `DrawerLayout`: set `android:fitsSystemWindows="true"` on DrawerLayout, apply `ViewCompat.setOnApplyWindowInsetsListener` on the inner content `LinearLayout` id (e.g. `settingsContentLayout`, `interviewContentLayout`).
- Activities using `CoordinatorLayout + AppBarLayout`: set `fitsSystemWindows="true"` on `AppBarLayout`.

## Build

```bash
./gradlew assembleDebug   # builds debug APK
./gradlew installDebug    # installs on connected device
```

## Key strings

`R.string.navigation_drawer_open` / `navigation_drawer_close` — used by ActionBarDrawerToggle.
