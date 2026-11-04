package com.redmagic.fps

import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val Red = Color(0xFFDC213B)
private const val KEY = "vendor.gpp.allgame.enable"
private const val REQUEST = 104

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleSupport.wrap(newBase))
    }
    private val prefs by lazy { getSharedPreferences("state", MODE_PRIVATE) }
    private val lock = Mutex()
    private var connected by mutableStateOf(false)
    private var permission by mutableStateOf(false)
    private var shell by mutableStateOf(false)
    private var resumeVersion by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var value by mutableStateOf<String?>(null)
    private var error by mutableStateOf<String?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var reset by mutableStateOf(false)
    private var osChanged by mutableStateOf(false)
    private var widgetAction = false
    private val args by lazy { Shizuku.UserServiceArgs(ComponentName(this, PropertyService::class.java)).tag("property-service").processNameSuffix("property").version(2).daemon(false) }
    private val received = Shizuku.OnBinderReceivedListener { runOnUiThread { updateConnection(); refresh() } }
    private val dead = Shizuku.OnBinderDeadListener { runOnUiThread { connected = false; permission = false; shell = false; value = null; error = getString(R.string.shizuku_lost); saveWidget("unknown") } }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST) runOnUiThread {
            updateConnection()
            if (result == PackageManager.PERMISSION_GRANTED) refresh() else error = getString(R.string.permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestedByWidget = consumeWidgetIntent(intent)
        // A recreated activity must not replay the previous widget tap.
        widgetAction = savedInstanceState == null && requestedByWidget
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        setContent { Screen() }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); widgetAction = consumeWidgetIntent(intent); refresh() }
    private fun isWidgetIntent(intent: Intent?): Boolean = intent?.action == "com.redmagic.fps.WIDGET_TOGGLE" && intent.getStringExtra("token")?.let { it == prefs.getString("widget_token", null) } == true
    private fun consumeWidgetIntent(intent: Intent?): Boolean {
        val requested = isWidgetIntent(intent)
        if (intent?.action == "com.redmagic.fps.WIDGET_TOGGLE") {
            intent.action = Intent.ACTION_MAIN
            intent.removeExtra("token")
            setIntent(intent)
        }
        return requested
    }
    override fun onResume() { super.onResume(); resumeVersion++; updateConnection(); refresh() }
    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead); Shizuku.removeRequestPermissionResultListener(permissionResult)
        super.onDestroy()
    }
    private fun updateConnection() {
        try {
            connected = Shizuku.pingBinder()
            permission = connected && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            shell = permission && Shizuku.getUid() == 2000
        } catch (_: Exception) { connected = false; permission = false; shell = false }
    }
    private fun requestAccess() {
        updateConnection()
        if (!connected) { error = getString(R.string.start_shizuku); return }
        try {
            if (Shizuku.isPreV11()) error = getString(R.string.shizuku_version)
            else if (Shizuku.shouldShowRequestPermissionRationale()) error = getString(R.string.permission_rationale)
            else Shizuku.requestPermission(REQUEST)
        } catch (e: Exception) { error = getString(R.string.shizuku_error, e.message ?: e.javaClass.simpleName) }
    }
    private suspend fun <T> service(block: (IPropertyService) -> T): T = lock.withLock {
        withContext(Dispatchers.IO) {
            var connection: ServiceConnection? = null
            try {
                val binder = try {
                    withTimeout(12000) {
                        suspendCancellableCoroutine<IPropertyService> { continuation ->
                            val c = object : ServiceConnection {
                                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                                    if (continuation.isActive) {
                                        if (binder == null) continuation.resumeWithException(IllegalStateException(getString(R.string.binder_empty)))
                                        else continuation.resume(IPropertyService.Stub.asInterface(binder))
                                    }
                                }
                                override fun onServiceDisconnected(name: ComponentName?) {
                                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(getString(R.string.service_disconnected)))
                                }
                            }
                            connection = c
                            try { Shizuku.bindUserService(args, c) } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    throw IllegalStateException(getString(R.string.service_timeout), e)
                }
                block(binder)
            } finally { connection?.let { try { Shizuku.unbindUserService(args, it, true); Shizuku.unbindUserService(args, it, false) } catch (_: Exception) {} } }
        }
    }
    private fun checked(result: Array<String>): String {
        if (result.size < 3 || result[0] != "0") {
            val detail = when (val raw = result.getOrNull(2).orEmpty()) {
                "FPS_UID_REQUIRED" -> getString(R.string.shell_required)
                "FPS_TIMEOUT" -> getString(R.string.service_timeout)
                "FPS_INVALID_VALUE" -> getString(R.string.invalid_value)
                else -> raw.ifBlank { getString(R.string.command_failed) }
            }
            throw IllegalStateException(getString(R.string.command_error, result.getOrNull(0) ?: "?", detail))
        }
        return result[1]
    }
    private fun refresh() {
        updateConnection()
        if (!permission || busy) {
            if (!permission) { value = null; saveWidget("unknown"); widgetAction = false }
            return
        }
        busy = true
        lifecycleScope.launch {
            try {
                if (Shizuku.getUid() != 2000) throw IllegalStateException(getString(R.string.shell_required))
                val current = service { checked(it.readProperty()) }
                value = current; error = null
                val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
                reset = prefs.getBoolean("enabled_by_user", false) && boot >= 0 && prefs.getInt("enabled_boot", -1) >= 0 && boot != prefs.getInt("enabled_boot", -1) && current != "1"
                osChanged = prefs.getString("last_os", null)?.let { it != Build.DISPLAY } == true
                saveWidget(widgetState(current))
                val shouldToggle = widgetAction
                widgetAction = false
                if (shouldToggle) lifecycleScope.launch { yield(); toggle() }
            } catch (e: Exception) { if (e is CancellationException) throw e; value = null; error = getString(R.string.read_failed, e.message ?: e.javaClass.simpleName); saveWidget("error"); widgetAction = false }
            finally { busy = false }
        }
    }
    private fun change(target: Int) {
        if (busy || !permission) return
        busy = true; notice = null
        lifecycleScope.launch {
            try {
                if (Shizuku.getUid() != 2000) throw IllegalStateException(getString(R.string.shell_required))
                val actual = service { svc ->
                    val before = checked(svc.readProperty())
                    if (!prefs.contains("original")) prefs.edit().putString("original", before).apply()
                    checked(svc.writeProperty(target))
                    checked(svc.readProperty())
                }
                value = actual; saveWidget(widgetState(actual))
                if (actual != target.toString()) throw IllegalStateException(getString(R.string.write_mismatch, actual.ifEmpty { getString(R.string.empty_value) }, target.toString()))
                prefs.edit().putBoolean("enabled_by_user", target == 1).putInt("enabled_boot", Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)).putString("last_os", Build.DISPLAY).apply()
                reset = false; osChanged = false; error = null
                notice = getString(if (target == 1) R.string.system_on_notice else R.string.system_off_notice)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = getString(R.string.write_failed, e.message ?: e.javaClass.simpleName)
                // A write may have succeeded despite a failed read; avoid displaying an assumed state.
                value = null; saveWidget("error")
            } finally { busy = false }
        }
    }
    private fun toggle() { if (value in listOf("", "0", "1")) change(if (value == "1") 0 else 1) else error = getString(R.string.unknown_value_error) }
    private fun status(v: String?) = getString(when (v) { "1" -> R.string.status_on; "0" -> R.string.status_off; else -> R.string.status_unknown })
    private fun widgetState(v: String?) = when (v) { "1" -> "on"; "0" -> "off"; else -> "unknown" }
    private fun saveWidget(s: String) { prefs.edit().putString("widget_state", s).apply(); UnlockWidget.update(this@MainActivity) }
    private fun customGames(): List<AppEntry> = prefs.getStringSet("custom_games", emptySet()).orEmpty().sorted().map { pkg ->
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { prefs.getString("label:$pkg", pkg) ?: pkg }
        AppEntry(pkg, label, custom = true)
    }
    private fun availableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0).mapNotNull { info ->
            val app = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (app.packageName == packageName) return@mapNotNull null
            val label = try { info.loadLabel(packageManager)?.toString().orEmpty().ifBlank { app.packageName } } catch (_: Exception) { app.packageName }
            @Suppress("DEPRECATION")
            val detectedGame = app.category == ApplicationInfo.CATEGORY_GAME || app.flags and ApplicationInfo.FLAG_IS_GAME != 0
            val entry = AppEntry(app.packageName, label, detectedGame = detectedGame)
            val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0 && app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
            if (system && app.category != ApplicationInfo.CATEGORY_GAME && AppOrdering.priority(entry, null) == 5) return@mapNotNull null
            entry
        }.distinctBy { it.pkg }
    }
    private fun isInstalled(pkg: String): Boolean = try { packageManager.getApplicationInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    private fun canLaunch(pkg: String): Boolean = try { packageManager.getLaunchIntentForPackage(pkg) != null } catch (_: Exception) { false }
    private fun launchGame(game: AppEntry) {
        val intent = packageManager.getLaunchIntentForPackage(game.pkg)
        if (intent == null) {
            val message = if (game.gameHub) getString(R.string.gamehub_not_found) else getString(R.string.game_not_found, game.name)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        try { startActivity(intent) } catch (e: Exception) { Toast.makeText(this, getString(R.string.launch_error, game.name, e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show() }
    }
    private fun openShizukuPage(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_browser), Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, getString(R.string.browser_error), Toast.LENGTH_LONG).show()
        }
    }
    private fun markLabel(mark: Int): String = getString(when (mark) { 1 -> R.string.mark_works; 2 -> R.string.mark_fails; else -> R.string.mark_unknown })

    private fun hideCustomGame(game: AppEntry) {
        val custom = prefs.getStringSet("custom_games", emptySet()).orEmpty() - game.pkg
        val hidden = prefs.getStringSet("hidden_apps", emptySet()).orEmpty() + game.pkg
        prefs.edit().putStringSet("custom_games", custom).putStringSet("hidden_apps", hidden)
            .remove("mark:${game.pkg}").remove("label:${game.pkg}").apply()
    }

    @Composable private fun AppCard(game: AppEntry, revision: Int, onMark: () -> Unit, onVersion: () -> Unit, onRemove: () -> Unit) {
        val installed = remember(game.pkg, resumeVersion, revision) { isInstalled(game.pkg) }
        val launchable = remember(game.pkg, resumeVersion, revision) { canLaunch(game.pkg) }
        val mark = remember(game.pkg, revision) { prefs.getInt("mark:${game.pkg}", 0) }
        Surface(color = Color(0xFF202028), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF3B2029)) { Text(game.name.take(1).uppercase(), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFFFF8592), fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) { Text(game.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Caption(when { !installed -> stringResource(R.string.not_installed); launchable -> stringResource(R.string.installed_launchable); else -> stringResource(R.string.installed_unlaunchable) }) }
                }
                Caption(markLabel(mark), if (mark == 1) Color(0xFF9DE6B5) else Color(0xFFAAAAB7))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { launchGame(game) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.open)) }
                    TextButton(onClick = onMark, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.mark)) }
                }
                if (game.custom || game.gameHub) Row {
                    if (game.gameHub) TextButton(onClick = onVersion, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.version)) }
                    if (game.custom) TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(stringResource(R.string.remove)) }
                }
            }
        }
    }

    @Composable private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
        Surface(modifier = modifier.fillMaxWidth().animateContentSize(), color = Color(0xFF15151C), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFF292930))) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
    @Composable private fun Eyebrow(text: String) { Text(text, color = Color(0xFFFF5B6D), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.7.sp) }
    @Composable private fun Caption(text: String, color: Color = Color(0xFFAAAAB7)) { Text(text, color = color, fontSize = 13.sp, lineHeight = 19.sp) }
    @Composable private fun Heading(text: String) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) }

    // These diagrams are drawn in Compose from the supplied reference photos; they are not interactive controls.
    @Composable private fun GuideIllustration(step: String) {
        Surface(color = Color(0xFF242329), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF45434B)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Caption(stringResource(R.string.diagram_label), Color(0xFFAAA7B2))
                if (step == "04") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF111116)) { Text("▦  ↑", color = Color.White, fontSize = 23.sp, modifier = Modifier.padding(10.dp)) }
                        Column(Modifier.weight(1f)) { Text(stringResource(R.string.image_enhance), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Caption(stringResource(R.string.find_tile)) }
                    }
                } else {
                    Text(stringResource(R.string.image_enhance), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Caption(stringResource(R.string.image_quality), Color(0xFFC0BBC8))
                    Text(stringResource(R.string.options_line), color = Color(0xFFC0BBC8), fontSize = 11.sp)
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF45222C), border = BorderStroke(1.dp, Color(0xFFB8495B))) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(stringResource(R.string.frame_rate_arrow), color = Color(0xFFFF9BA7), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(stringResource(R.string.options_line), color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                    Caption(stringResource(R.string.diagram_hint))
                }
            }
        }
    }
    @Composable private fun Screen() {
        val clipboard = LocalClipboardManager.current
        var instructions by rememberSaveable { mutableStateOf(false) }
        var showLibrary by rememberSaveable { mutableStateOf(false) }
        var showLanguage by rememberSaveable { mutableStateOf(!prefs.contains(LocaleSupport.KEY)) }
        var draftLanguage by rememberSaveable { mutableStateOf(LocaleSupport.language(this@MainActivity)) }
        var picker by remember { mutableStateOf(false) }
        var pickerForGameHub by remember { mutableStateOf(false) }
        var showOtherApps by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<AppEntry?>(null) }
        var revision by remember { mutableIntStateOf(0) }
        var scannedApps by remember { mutableStateOf<List<AppEntry>?>(null) }
        var scanError by remember { mutableStateOf(false) }
        val homeScroll = rememberScrollState()
        val guideScroll = rememberScrollState()
        LaunchedEffect(resumeVersion) {
            try {
                scannedApps = withContext(Dispatchers.IO) { availableApps() }
                scanError = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                scanError = true
            }
        }
        val games = remember(scannedApps, revision, resumeVersion) {
            AppOrdering.merge(scannedApps.orEmpty(), customGames(), prefs.getStringSet("hidden_apps", emptySet()).orEmpty(), prefs.getString("gamehub_package", null))
        }
        val quickGames = games.filter { it.available }.take(3)
        val buttonColor by animateColorAsState(if (value == "1") Color(0xFF712631) else Red, label = "unlock")
        BackHandler(enabled = !picker && editing == null && !showLanguage) {
            if (instructions) instructions = false
            else if (showLibrary) showLibrary = false
            else Toast.makeText(this, getString(R.string.home_already), Toast.LENGTH_SHORT).show()
        }
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFF7382), onPrimary = Color(0xFF220007), background = Color(0xFF09090E), onBackground = Color(0xFFF7F4F8), surface = Color(0xFF17171F), onSurface = Color(0xFFF7F4F8), onSurfaceVariant = Color(0xFFD1CBD4))) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
              Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (instructions) {
                Column(Modifier.fillMaxSize().background(Color(0xFF09090E)).verticalScroll(guideScroll).padding(horizontal = 22.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextButton(onClick = { instructions = false }) { Text(stringResource(R.string.back)) }
                    Eyebrow(stringResource(R.string.guide_eyebrow))
                    Text(stringResource(R.string.guide_title), fontSize = 32.sp, lineHeight = 37.sp, fontWeight = FontWeight.Black)
                    Caption(stringResource(R.string.guide_intro))
                    val steps = listOf(
                        "01" to (stringResource(R.string.guide_1_title) to stringResource(R.string.guide_1_body)),
                        "02" to (stringResource(R.string.guide_2_title) to stringResource(R.string.guide_2_body)),
                        "03" to (stringResource(R.string.guide_3_title) to stringResource(R.string.guide_3_body)),
                        "04" to (stringResource(R.string.guide_4_title) to stringResource(R.string.guide_4_body)),
                        "05" to (stringResource(R.string.guide_5_title) to stringResource(R.string.guide_5_body)),
                        "06" to (stringResource(R.string.guide_6_title) to stringResource(R.string.guide_6_body))
                    )
                    steps.forEach { (number, step) ->
                        Panel { Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                            Text(number, color = Color(0xFFFF7382), fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(step.first, fontWeight = FontWeight.Bold, fontSize = 16.sp); Caption(step.second) }
                        }; if (number == "04" || number == "05") GuideIllustration(number) }
                    }
                    Caption(stringResource(R.string.guide_disclaimer))
                    Spacer(Modifier.height(20.dp))
                }
            } else if (showLibrary) {
                LazyColumn(Modifier.fillMaxSize().background(Color(0xFF09090E)), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        TextButton(onClick = { showLibrary = false }) { Text(stringResource(R.string.back)) }
                        Eyebrow(stringResource(R.string.library_eyebrow))
                        Heading(stringResource(R.string.installed_games))
                        Caption(stringResource(R.string.library_intro))
                    }
                    if (scanError) item { Caption(stringResource(R.string.scan_failed), Color(0xFFFF9BA7)) }
                    items(games, key = { it.pkg }) { game ->
                        AppCard(game, revision, onMark = { editing = game }, onVersion = { pickerForGameHub = true; showOtherApps = false; picker = true }, onRemove = { hideCustomGame(game); revision++ })
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            } else {
                Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C0B12), Color(0xFF09090E), Color(0xFF09090E)))).verticalScroll(homeScroll).padding(horizontal = 20.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = Red, shape = RoundedCornerShape(9.dp)) { Text("✦", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Color.White, fontSize = 20.sp) }
                        Column { Eyebrow("REDMAGIC  /  ${BuildConfig.VERSION_NAME}"); Text("FPS+", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Color.White) }
                    }
                    TextButton(onClick = { draftLanguage = LocaleSupport.language(this@MainActivity); showLanguage = true }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("🌐  " + stringResource(R.string.language_button)) }
                    Caption(stringResource(R.string.home_tagline))
                    Panel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Eyebrow(stringResource(R.string.connection)); Spacer(Modifier.height(6.dp)); Text("Shizuku", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                            Text(if (connected) stringResource(R.string.online) else stringResource(R.string.offline), color = if (connected) Color(0xFF8CE5AE) else Color(0xFFFF9DA7), fontSize = 13.sp)
                        }
                        Caption(when { !connected -> stringResource(R.string.start_shizuku); !permission -> stringResource(R.string.allow_shizuku); shell -> stringResource(R.string.shell_ready); else -> stringResource(R.string.shell_required) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text(stringResource(R.string.recheck)) }
                            if (connected && !permission) Button(onClick = ::requestAccess) { Text(stringResource(R.string.allow)) }
                        }
                        TextButton(onClick = { openShizukuPage("https://shizuku.rikka.app/guide/setup/") }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.official_guide)) }
                        TextButton(onClick = { openShizukuPage("https://shizuku.rikka.app/download/") }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.official_download)) }
                    }
                    Panel {
                        Eyebrow(stringResource(R.string.system_unlock))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(if (error != null && value == null) stringResource(R.string.status_error) else status(value), fontSize = 29.sp, fontWeight = FontWeight.Black); Caption(stringResource(R.string.property_state)) }
                            Surface(shape = RoundedCornerShape(50), color = if (value == "1") Color(0xFF44232A) else Color(0xFF26262E)) { Text("◈", color = if (value == "1") Color(0xFFFF6575) else Color.Gray, fontSize = 30.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                        }
                        Caption("$KEY = ${value?.ifEmpty { stringResource(R.string.empty_value) } ?: "—"}", Color(0xFFBDB8C2))
                        Button(onClick = ::toggle, enabled = shell && !busy && value != null, colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = Color.White, disabledContainerColor = Color(0xFF343039), disabledContentColor = Color(0xFFCBC4CC)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)) {
                            Text(if (busy) stringResource(R.string.checking) else if (value == "1") stringResource(R.string.disable_unlock) else stringResource(R.string.enable_unlock), fontWeight = FontWeight.Bold)
                        }
                        if (reset) Caption(stringResource(R.string.reboot_reset), Color(0xFFFFC67A))
                        if (osChanged) Caption(stringResource(R.string.os_changed), Color(0xFFFFC67A))
                        error?.let { Caption(it, Color(0xFFFF9BA7)) }
                        notice?.let { Caption(it, Color(0xFF9DE6B5)) }
                    }
                    Panel {
                        Eyebrow(stringResource(R.string.interpolation))
                        Heading(if (value == "1") stringResource(R.string.system_on) else if (value == "0") stringResource(R.string.system_off) else stringResource(R.string.system_unknown))
                        Caption(stringResource(R.string.interpolation_disclaimer))
                    }
                    Panel {
                        Eyebrow(stringResource(R.string.your_library))
                        Heading(stringResource(R.string.quick_launch))
                        Caption(stringResource(R.string.quick_hint))
                        if (scannedApps == null && !scanError) Caption(stringResource(R.string.scanning))
                        else if (scanError) Caption(stringResource(R.string.scan_failed), Color(0xFFFF9BA7))
                        else if (quickGames.isEmpty()) Caption(stringResource(R.string.no_games))
                        quickGames.forEach { game ->
                            AppCard(game, revision, onMark = { editing = game }, onVersion = { pickerForGameHub = true; showOtherApps = false; picker = true }, onRemove = { hideCustomGame(game); revision++ })
                        }
                        if (games.size > quickGames.size) OutlinedButton(onClick = { showLibrary = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.all_games, games.size)) }
                        OutlinedButton(onClick = { pickerForGameHub = false; showOtherApps = false; picker = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_game_plus)) }
                    }
                    Panel {
                        Eyebrow(stringResource(R.string.getting_started))
                        Heading(stringResource(R.string.how_to))
                        Caption(stringResource(R.string.how_to_hint))
                        OutlinedButton(onClick = { instructions = true }) { Text(stringResource(R.string.open_guide)) }
                    }
                    Panel {
                        Eyebrow(stringResource(R.string.device))
                        Heading(Build.MODEL)
                        Caption(stringResource(R.string.build_info, Build.MANUFACTURER, Build.VERSION.RELEASE, Build.DISPLAY))
                        if (!Build.MODEL.startsWith("NX809J", true)) Caption(stringResource(R.string.compat_warning), Color(0xFFFFC67A))
                        val original = prefs.getString("original", null)
                        if (original == "0" || original == "1") OutlinedButton(onClick = { change(original.toInt()) }, enabled = shell && !busy) { Text(stringResource(R.string.restore_value, original)) }
                        else if (original == "") Caption(stringResource(R.string.original_empty))
                        else if (original != null) Caption(stringResource(R.string.original_other, original))
                        TextButton(onClick = {
                            val connection = getString(when { shell -> R.string.report_shell; permission -> R.string.report_nonshell; connected -> R.string.report_no_permission; else -> R.string.report_disconnected })
                            val report = listOf("FPS+ ${BuildConfig.VERSION_NAME}", getString(R.string.report_model, "${Build.MANUFACTURER} ${Build.MODEL}"), "Android: ${Build.VERSION.RELEASE}", getString(R.string.report_build, Build.DISPLAY), "Shizuku: $connection", "$KEY: ${value?.ifEmpty { getString(R.string.empty_value) } ?: getString(R.string.report_unknown)}", "${getString(R.string.status_error)}: ${error ?: getString(R.string.report_none)}", getString(R.string.report_interp)).joinToString("\n")
                            clipboard.setText(AnnotatedString(report))
                            notice = getString(R.string.report_copied)
                        }) { Text(stringResource(R.string.copy_report)) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (picker) {
                val options = remember(pickerForGameHub, showOtherApps, revision, scannedApps) {
                    val savedPackages = prefs.getStringSet("custom_games", emptySet()).orEmpty()
                    val selectedHub = prefs.getString("gamehub_package", null)
                    scannedApps.orEmpty().filter { app ->
                        val rank = AppOrdering.priority(app, selectedHub)
                        (pickerForGameHub || app.pkg !in savedPackages) &&
                            (showOtherApps || if (pickerForGameHub) rank == 0 else app.detectedGame || rank < 5)
                    }.sortedWith(compareBy({ AppOrdering.priority(it, selectedHub) }, { it.name.lowercase() }))
                }
                AlertDialog(onDismissRequest = { picker = false }, title = { Text(if (pickerForGameHub) stringResource(R.string.choose_gamehub) else stringResource(R.string.add_game)) }, text = {
                    Column {
                        Caption(stringResource(R.string.picker_hint))
                        TextButton(onClick = { showOtherApps = !showOtherApps }) { Text(stringResource(if (showOtherApps) R.string.show_detected_games else R.string.show_other_apps)) }
                        if (options.isEmpty()) Caption(when {
                            scannedApps == null && !scanError -> stringResource(R.string.scanning)
                            scanError -> stringResource(R.string.scan_failed)
                            !showOtherApps -> stringResource(R.string.picker_no_detected)
                            else -> stringResource(R.string.picker_empty)
                        }) else LazyColumn(Modifier.heightIn(max = 350.dp)) {
                            items(options, key = { it.pkg }) { game ->
                                Column(Modifier.fillMaxWidth().clickable {
                                    val hidden = prefs.getStringSet("hidden_apps", emptySet()).orEmpty() - game.pkg
                                    val editor = prefs.edit().putStringSet("hidden_apps", hidden)
                                    if (pickerForGameHub) editor.putString("gamehub_package", game.pkg)
                                    else editor.putStringSet("custom_games", prefs.getStringSet("custom_games", emptySet()).orEmpty() + game.pkg).putString("label:${game.pkg}", game.name)
                                    editor.apply()
                                    revision++; picker = false
                                }.padding(vertical = 12.dp)) { Text(game.name, fontWeight = FontWeight.Medium); Caption(game.pkg) }
                                HorizontalDivider(color = Color(0xFF30303A))
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.close)) } })
            }
            editing?.let { game ->
                AlertDialog(onDismissRequest = { editing = null }, title = { Text(game.name) }, text = {
                    Column { Caption(stringResource(R.string.personal_mark)); Spacer(Modifier.height(8.dp)); listOf(stringResource(R.string.not_checked), stringResource(R.string.checked_works), stringResource(R.string.does_not_work)).forEachIndexed { index, label ->
                        TextButton(onClick = { prefs.edit().putInt("mark:${game.pkg}", index).apply(); revision++; editing = null }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    } }
                }, confirmButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } })
            }
            if (showLanguage) {
                val firstChoice = !prefs.contains(LocaleSupport.KEY)
                val preview = remember(draftLanguage) { LocaleSupport.contextFor(this@MainActivity, draftLanguage) }
                AlertDialog(
                    onDismissRequest = { if (!firstChoice) showLanguage = false },
                    properties = DialogProperties(dismissOnBackPress = !firstChoice, dismissOnClickOutside = !firstChoice),
                    title = { Text(preview.getString(R.string.language_title)) },
                    text = {
                        Column {
                            Caption(preview.getString(R.string.language_hint))
                            Spacer(Modifier.height(8.dp))
                            LocaleSupport.supported.zip(LocaleSupport.names).forEach { (code, name) ->
                                Row(Modifier.fillMaxWidth().clickable { draftLanguage = code }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = draftLanguage == code, onClick = { draftLanguage = code })
                                    Text(name)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val previous = LocaleSupport.language(this@MainActivity)
                            prefs.edit().putString(LocaleSupport.KEY, draftLanguage).apply()
                            UnlockWidget.update(this@MainActivity)
                            showLanguage = false
                            if (draftLanguage != previous) recreate()
                        }) { Text(preview.getString(R.string.continue_button)) }
                    },
                    dismissButton = { if (!firstChoice) TextButton(onClick = { showLanguage = false }) { Text(preview.getString(R.string.cancel)) } }
                )
            }
              }
            }
        }
    }
}
