package com.example.susnet

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Canvas
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import com.example.susnet.data.BaselineStore
import com.example.susnet.data.SusnetDatabase
import com.example.susnet.detection.EvilTwinDetector
import com.example.susnet.detection.LatencyMonitor
import com.example.susnet.detection.NetworkConfigMonitor
import com.example.susnet.detection.SignalStabilityMonitor
import com.example.susnet.detection.ThreatEvaluator
import com.example.susnet.detection.TlsPinningMonitor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.susnet.detection.ThreatEvaluatorFactory
import com.example.susnet.notification.AutoCheckManager

class MainActivity : AppCompatActivity() {

    private lateinit var threatEvaluator: ThreatEvaluator
    private lateinit var tlsPinningMonitor: TlsPinningMonitor
    private var latestResult: ThreatEvaluator.ThreatResult? = null

    private var isEvaluating = false
    private var lastUpdatedMillis: Long = 0
    private var rotateAnimator: ObjectAnimator? = null

    private fun openSystemNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Buka Pengaturan Notifikasi di HP Anda", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setNotifyEnabled(true)
        } else {
            setNotifyEnabled(false)
            val root = findViewById<View>(R.id.main) ?: findViewById<View>(android.R.id.content)
            if (root != null) {
                Snackbar.make(root, "Izin notifikasi diperlukan untuk menampilkan peringatan WiFi", Snackbar.LENGTH_LONG)
                    .setAction("Pengaturan") {
                        openSystemNotificationSettings()
                    }
                    .show()
            }
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            runEvaluation()
        } else {
            Toast.makeText(this, "Izin lokasi diperlukan untuk memindai WiFi", Toast.LENGTH_SHORT).show()
            findViewById<TextView>(R.id.textSsid).text = "Izin lokasi diperlukan"
            findViewById<TextView>(R.id.textStatusDetail).text = "Izinkan akses lokasi agar nama dan alamat WiFi bisa dibaca"
            findViewById<TextView>(R.id.textScore).text = "–"
            findViewById<TextView>(R.id.textStatusLabel).text = "Tidak aktif"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            
            val navContainer = findViewById<View>(R.id.layoutBottomNavContainer)
            navContainer?.let { nav ->
                val params = nav.layoutParams as? ViewGroup.MarginLayoutParams
                if (params != null) {
                    val baseMarginBottom = (16 * resources.displayMetrics.density).toInt()
                    params.bottomMargin = baseMarginBottom + systemBars.bottom
                    nav.layoutParams = params
                }
            }
            insets
        }

        setupThreatEvaluator()
        setupAnimations()
        setupNightMode()
        setupBottomNavigation()
        setupHistoryRecyclerView()

        if (hasLocationPermission()) {
            runEvaluation()
        } else {
            checkLocationPermissions()
        }

        findViewById<View>(R.id.buttonDetail).setOnClickListener {
            openDetailScreen()
        }

        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            runEvaluation()
        }
    }

    private var currentSelectedTab: Int = 0

    private fun setupBottomNavigation() {
        val scrollContent = findViewById<View>(R.id.scrollContent)
        val scrollHistory = findViewById<View>(R.id.scrollHistory)
        val scrollSettings = findViewById<View>(R.id.scrollSettings)

        val navItemHome = findViewById<View>(R.id.navItemHome)
        val navItemHistory = findViewById<View>(R.id.navItemHistory)
        val navItemSettings = findViewById<View>(R.id.navItemSettings)

        val pillHome = findViewById<View>(R.id.pillHome)
        val pillHistory = findViewById<View>(R.id.pillHistory)
        val pillSettings = findViewById<View>(R.id.pillSettings)

        fun selectTab(tabIndex: Int) {
            currentSelectedTab = tabIndex
            val statusLevel = StatusLevel.fromScore(latestResult?.threatLevel ?: 0)
            val palette = StatusPalette.from(statusLevel, this)

            scrollContent?.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
            scrollHistory?.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
            scrollSettings?.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE

            if (tabIndex == 1) {
                loadHistoryData()
            } else if (tabIndex == 2) {
                setupSettingsTab()
            }

            updateNavTabAppearance(tabIndex, palette)
        }

        navItemHome?.setOnClickListener { selectTab(0) }
        pillHome?.setOnClickListener { selectTab(0) }

        navItemHistory?.setOnClickListener { selectTab(1) }
        pillHistory?.setOnClickListener { selectTab(1) }

        navItemSettings?.setOnClickListener { selectTab(2) }
        pillSettings?.setOnClickListener { selectTab(2) }

        selectTab(0)
    }

    private fun updateNavTabAppearance(selectedIndex: Int, palette: StatusPalette) {
        val pills = listOf(
            findViewById<View>(R.id.pillHome),
            findViewById<View>(R.id.pillHistory),
            findViewById<View>(R.id.pillSettings)
        )

        val icons = listOf(
            findViewById<ImageView>(R.id.iconHome),
            findViewById<ImageView>(R.id.iconHistory),
            findViewById<ImageView>(R.id.iconSettings)
        )

        val textViews = listOf(
            findViewById<TextView>(R.id.textHome),
            findViewById<TextView>(R.id.textHistory),
            findViewById<TextView>(R.id.textSettings)
        )

        for (i in pills.indices) {
            val isSelected = (i == selectedIndex)
            val pill = pills[i]
            val icon = icons[i]
            val tv = textViews[i]

            val pillDrawable = pill?.background?.mutate() as? GradientDrawable
            if (isSelected) {
                pillDrawable?.setColor(palette.chipBg)
                icon?.setColorFilter(palette.textAccent)
                tv?.setTextColor(palette.textAccent)
                tv?.setTypeface(null, android.graphics.Typeface.BOLD)

                pill?.animate()?.scaleX(1.05f)?.scaleY(1.05f)?.setDuration(180)?.start()
            } else {
                pillDrawable?.setColor(android.graphics.Color.TRANSPARENT)
                icon?.setColorFilter(palette.secondary)
                tv?.setTextColor(palette.secondary)
                tv?.setTypeface(null, android.graphics.Typeface.NORMAL)

                pill?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(180)?.start()
            }
        }
    }

    private val historyAdapter = HistoryAdapter()

    private fun setupHistoryRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerHistory) ?: return
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = historyAdapter

        val clearAllBtn = findViewById<TextView>(R.id.buttonClearAllHistory)
        clearAllBtn?.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Hapus semua riwayat?")
                .setMessage("Riwayat pemindaian akan dihapus. Data jaringan yang sudah dikenali tidak ikut terhapus.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus") { _, _ ->
                    historyAdapter.submitList(emptyList())
                    updateHistoryEmptyState(emptyList<com.example.susnet.data.HistoryItem>())
                    lifecycleScope.launch {
                        try {
                            val db = SusnetDatabase.getInstance(applicationContext)
                            db.detectionEventDao().deleteAll()
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to delete all history", e)
                        }
                    }
                }
                .show()
        }

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = historyAdapter.currentList.getOrNull(position) ?: return

                val currentList = historyAdapter.currentList.toMutableList()
                currentList.removeAt(position)
                historyAdapter.submitList(currentList)
                updateHistoryEmptyState(currentList)

                lifecycleScope.launch {
                    try {
                        val db = SusnetDatabase.getInstance(applicationContext)
                        db.detectionEventDao().deleteById(item.event.id)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to delete history item", e)
                    }
                }

                Snackbar.make(recyclerView, "Riwayat dihapus", Snackbar.LENGTH_LONG)
                    .setAction("Urungkan") {
                        currentList.add(position, item)
                        historyAdapter.submitList(currentList.toList())
                        updateHistoryEmptyState(currentList)

                        lifecycleScope.launch {
                            try {
                                val db = SusnetDatabase.getInstance(applicationContext)
                                db.detectionEventDao().insert(item.event)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to undo delete history item", e)
                            }
                        }
                    }
                    .show()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val density = resources.displayMetrics.density
                val background = GradientDrawable().apply {
                    cornerRadius = 16f * density
                    setColor(ContextCompat.getColor(this@MainActivity, R.color.text_danger))
                }
                val icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_delete)

                if (dX > 0) { // Swiping right
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    background.draw(c)
                    icon?.let {
                        val iconMargin = (16 * density).toInt()
                        val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                        val iconLeft = itemView.left + iconMargin
                        val iconRight = iconLeft + it.intrinsicWidth
                        val iconBottom = iconTop + it.intrinsicHeight
                        it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        it.draw(c)
                    }
                } else if (dX < 0) { // Swiping left
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    background.draw(c)
                    icon?.let {
                        val iconMargin = (16 * density).toInt()
                        val iconRight = itemView.right - iconMargin
                        val iconLeft = iconRight - it.intrinsicWidth
                        val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                        val iconBottom = iconTop + it.intrinsicHeight
                        it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        it.draw(c)
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)
    }

    private fun updateHistoryEmptyState(list: List<*>) {
        val emptyView = findViewById<View>(R.id.textHistoryEmpty)
        val clearAllBtn = findViewById<TextView>(R.id.buttonClearAllHistory)
        if (list.isEmpty()) {
            emptyView?.visibility = View.VISIBLE
            clearAllBtn?.visibility = View.GONE
        } else {
            emptyView?.visibility = View.GONE
            clearAllBtn?.visibility = View.VISIBLE
        }
    }

    private fun loadHistoryData() {
        lifecycleScope.launch {
            try {
                val db = SusnetDatabase.getInstance(applicationContext)
                val events = db.detectionEventDao().getRecentHistoryItems(100)

                val statusLevelCurrent = StatusLevel.fromScore(latestResult?.threatLevel ?: 0)
                val palette = StatusPalette.from(statusLevelCurrent, this@MainActivity)

                historyAdapter.setPalette(palette)
                historyAdapter.submitList(events)
                updateHistoryEmptyState(events)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load history", e)
            }
        }
    }

    private fun setupSettingsTab() {
        val switchDarkMode = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSettingsDarkMode) ?: return
        val isNight = isNightModeActive()

        switchDarkMode.setOnCheckedChangeListener(null)
        switchDarkMode.isChecked = isNight

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("susnet_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("pref_night_mode", isChecked)
                .apply()

            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        val switchNotify = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSettingsNotifications)
        val prefs = getSharedPreferences("susnet_prefs", MODE_PRIVATE)
        val isNotifyEnabled = prefs.getBoolean("notify_enabled", false)

        switchNotify?.setOnCheckedChangeListener(null)
        switchNotify?.isChecked = isNotifyEnabled
        switchNotify?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@setOnCheckedChangeListener
                    }
                }

                if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    setNotifyEnabled(false)
                    val root = findViewById<View>(R.id.main) ?: findViewById<View>(android.R.id.content)
                    if (root != null) {
                        Snackbar.make(root, "Izin notifikasi dinonaktifkan di sistem HP Anda", Snackbar.LENGTH_LONG)
                            .setAction("Pengaturan") {
                                openSystemNotificationSettings()
                            }
                            .show()
                    }
                    return@setOnCheckedChangeListener
                }

                setNotifyEnabled(true)
            } else {
                setNotifyEnabled(false)
            }
        }

        val statusLevel = StatusLevel.fromScore(latestResult?.threatLevel ?: 0)
        val palette = StatusPalette.from(statusLevel, this)
        updateSettingsCards(palette)
    }

    private fun setNotifyEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("susnet_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("notify_enabled", enabled).apply()
        val switchNotify = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSettingsNotifications)
        switchNotify?.isChecked = enabled
        if (enabled) {
            AutoCheckManager.start(applicationContext)
        } else {
            AutoCheckManager.stop(applicationContext)
        }
    }

    private fun updateSettingsCards(palette: StatusPalette) {
        val cardDarkMode = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSettingsDarkMode)
        val cardNotifications = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSettingsNotifications)
        val cardAbout = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSettingsAbout)

        val density = resources.displayMetrics.density
        for (card in listOf(cardDarkMode, cardNotifications, cardAbout)) {
            card?.setCardBackgroundColor(palette.surface)
            card?.strokeColor = palette.border
            card?.strokeWidth = (1f * density).toInt()
        }
    }

    private fun setupNightMode() {
        val btnNightMode = findViewById<ImageButton>(R.id.btnNightMode)
        val isNight = isNightModeActive()
        btnNightMode.setImageResource(if (isNight) R.drawable.ic_light_mode else R.drawable.ic_night_mode)
        btnNightMode.contentDescription = if (isNight) "Mode Terang" else "Mode Malam"

        btnNightMode.setOnClickListener {
            val newNightMode = !isNightModeActive()
            getSharedPreferences("susnet_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("pref_night_mode", newNightMode)
                .apply()

            AppCompatDelegate.setDefaultNightMode(
                if (newNightMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun isNightModeActive(): Boolean {
        val prefs = getSharedPreferences("susnet_prefs", MODE_PRIVATE)
        if (prefs.contains("pref_night_mode")) {
            return prefs.getBoolean("pref_night_mode", false)
        }
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (!isEvaluating && hasLocationPermission() && (now - lastUpdatedMillis > 30000)) {
            runEvaluation()
        }
    }

    private fun setupAnimations() {
        val btnRefresh = findViewById<View>(R.id.btnRefresh)
        rotateAnimator = ObjectAnimator.ofFloat(btnRefresh, View.ROTATION, 0f, 360f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun toggleLoading(loading: Boolean) {
        val btnRefresh = findViewById<View>(R.id.btnRefresh)
        isEvaluating = loading
        if (loading) {
            btnRefresh.isEnabled = false
            btnRefresh.alpha = 0.5f
            rotateAnimator?.start()
        } else {
            btnRefresh.isEnabled = true
            btnRefresh.alpha = 1.0f
            rotateAnimator?.cancel()
            btnRefresh.rotation = 0f
        }
    }

    private fun runEvaluation() {
        if (isEvaluating) return

        lifecycleScope.launch {
            toggleLoading(true)
            try {
                val result = threatEvaluator.evaluateCurrentNetwork()
                lastUpdatedMillis = System.currentTimeMillis()
                updateUi(result)
            } catch (e: Exception) {
                Log.e("MainActivity", "Evaluation failed", e)
                Toast.makeText(this@MainActivity, "Gagal memperbarui, coba lagi", Toast.LENGTH_SHORT).show()
            } finally {
                toggleLoading(false)
            }
        }
    }

    private fun updateUi(result: ThreatEvaluator.ThreatResult) {
        latestResult = result
        val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdatedMillis))
        findViewById<TextView>(R.id.textLastUpdated).text = "Diperbarui $timeString"

        val statusLevel = if (result.isConnected) StatusLevel.fromScore(result.threatLevel) else StatusLevel.AMAN
        val palette = StatusPalette.from(statusLevel, this)

        findViewById<View>(R.id.main)?.setBackgroundColor(palette.bg)
        window.statusBarColor = palette.bg
        window.navigationBarColor = palette.navBg

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val isDark = isNightModeActive()
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark

        val navContainer = findViewById<com.google.android.material.card.MaterialCardView>(R.id.layoutBottomNavContainer)
        navContainer?.setCardBackgroundColor(palette.surface)
        navContainer?.strokeColor = palette.border
        navContainer?.strokeWidth = (1 * resources.displayMetrics.density).toInt()

        updateNavTabAppearance(currentSelectedTab, palette)
        updateSettingsCards(palette)

        if (!result.isConnected) {
            findViewById<TextView>(R.id.textSsid).text = "Tidak terhubung"
            findViewById<TextView>(R.id.textScore).text = "–"
            findViewById<TextView>(R.id.textStatusLabel).text = "Tidak terhubung"
            findViewById<TextView>(R.id.textStatusDetail).text = "Sambungkan ke WiFi dan aktifkan izin serta layanan lokasi, lalu tekan segarkan"
            findViewById<View>(R.id.statusCard)?.setBackgroundResource(R.drawable.bg_status_card_aman)
            findViewById<View>(R.id.layoutStatusBadgePill)?.background?.setTint(palette.chipBg)
            val iconStatus = findViewById<ImageView>(R.id.iconStatus)
            iconStatus?.setImageResource(R.drawable.ic_status_normal)
            iconStatus?.setColorFilter(palette.secondary)
            findViewById<TextView>(R.id.textStatusLabel)?.setTextColor(palette.secondary)
            findViewById<ScoreGaugeView>(R.id.scoreGaugeView)?.setScore(0, palette, animate = false)
            findViewById<TextView>(R.id.textStatusExtraPill)?.text = "Status: Tidak aktif"
            findViewById<Button>(R.id.btnSlotWaspadaDetail)?.visibility = View.GONE
            findViewById<Button>(R.id.btnSlotBahayaDisconnect)?.visibility = View.GONE
            findViewById<View>(R.id.textStatusExtraPill)?.visibility = View.VISIBLE
            findViewById<TextView>(R.id.textWifiSecurity)?.visibility = View.GONE
            return
        }

        findViewById<TextView>(R.id.textSsid).text = result.currentSsid ?: "Tidak terhubung"
        findViewById<TextView>(R.id.textScore).text = result.threatLevel.toString()

        val textWifiSecurity = findViewById<TextView>(R.id.textWifiSecurity)
        if (textWifiSecurity != null) {
            textWifiSecurity.visibility = View.VISIBLE
            textWifiSecurity.text = result.securityLabel
            if (result.securityType == com.example.susnet.detection.WifiSecurityChecker.SecurityType.OPEN || result.securityType == com.example.susnet.detection.WifiSecurityChecker.SecurityType.WEP) {
                textWifiSecurity.setTextColor(palette.textAccent)
            } else {
                textWifiSecurity.setTextColor(palette.secondary)
            }
        }

        val statusLabel: String
        val bgDrawableRes: Int

        when (statusLevel) {
            StatusLevel.AMAN -> {
                statusLabel = "Aman"
                bgDrawableRes = R.drawable.bg_status_card_aman
            }
            StatusLevel.WASPADA -> {
                statusLabel = "Waspada"
                bgDrawableRes = R.drawable.bg_status_card_waspada
            }
            StatusLevel.BAHAYA -> {
                statusLabel = "Bahaya"
                bgDrawableRes = R.drawable.bg_status_card_bahaya
            }
        }

        findViewById<View>(R.id.statusCard)?.setBackgroundResource(bgDrawableRes)
        findViewById<ScoreGaugeView>(R.id.scoreGaugeView)?.setScore(result.threatLevel, palette)

        findViewById<View>(R.id.layoutStatusBadgePill)?.background?.setTint(palette.chipBg)
        val iconStatus = findViewById<ImageView>(R.id.iconStatus)
        val textStatusLabel = findViewById<TextView>(R.id.textStatusLabel)

        textStatusLabel?.text = statusLabel
        textStatusLabel?.setTextColor(palette.textAccent)

        if (statusLevel == StatusLevel.AMAN) {
            iconStatus?.setImageResource(R.drawable.ic_status_normal)
            iconStatus?.setColorFilter(palette.textAccent)
        } else {
            iconStatus?.setImageResource(R.drawable.ic_status_changed)
            iconStatus?.setColorFilter(palette.textAccent)
        }

        Log.d(
            "SusnetAudit",
            "threatLevel=${result.threatLevel}, bssidScore=${result.bssidChangeScore}, tlsScore=${result.tlsChangeScore}, signalScore=${result.signalInstabilityScore}, latencyScore=${result.latencyDeviationScore}, configScore=${result.configChangeScore}, signalSamples=${result.signalSamples}, signalStdDev=${"%.2f".format(result.signalStdDev)}"
        )

        val abnormalCount = listOf(
            result.bssidChangeScore,
            result.tlsChangeScore,
            result.signalInstabilityScore,
            result.latencyDeviationScore,
            result.configChangeScore
        ).count { it >= 35 }

        val detailText = when {
            abnormalCount > 0 -> "$abnormalCount tanda tidak biasa terdeteksi"
            statusLevel == StatusLevel.AMAN -> "Semua sinyal terlihat normal"
            else -> "Beberapa sinyal sedikit menyimpang"
        }
        val textStatusDetail = findViewById<TextView>(R.id.textStatusDetail)
        textStatusDetail?.text = detailText
        textStatusDetail?.setTextColor(palette.secondary)

        val extraPill = findViewById<TextView>(R.id.textStatusExtraPill)
        val btnWaspada = findViewById<Button>(R.id.btnSlotWaspadaDetail)
        val btnBahaya = findViewById<Button>(R.id.btnSlotBahayaDisconnect)

        when (statusLevel) {
            StatusLevel.AMAN -> {
                btnWaspada?.visibility = View.GONE
                btnBahaya?.visibility = View.GONE
                extraPill?.visibility = View.VISIBLE

                extraPill?.setBackgroundResource(R.drawable.bg_network_info_pill)
                extraPill?.background?.setTint(palette.tile)
                extraPill?.setTextColor(palette.ink)

                val gateway = result.gatewayIp
                val dns = if (result.dnsServers.isNullOrEmpty()) "-" else result.dnsServers
                extraPill?.text = if (gateway != null) {
                    "GW $gateway · DNS $dns"
                } else {
                    "Info jaringan tidak tersedia"
                }
            }
            StatusLevel.WASPADA -> {
                extraPill?.visibility = View.GONE
                btnBahaya?.visibility = View.GONE
                btnWaspada?.visibility = View.VISIBLE

                btnWaspada?.backgroundTintList = ColorStateList.valueOf(palette.buttonBg)
                btnWaspada?.setTextColor(palette.buttonText)
                btnWaspada?.setOnClickListener {
                    openDetailScreen()
                }
            }
            StatusLevel.BAHAYA -> {
                extraPill?.visibility = View.GONE
                btnWaspada?.visibility = View.GONE
                btnBahaya?.visibility = View.VISIBLE

                btnBahaya?.text = "Buka pengaturan WiFi"
                btnBahaya?.backgroundTintList = ColorStateList.valueOf(palette.buttonBg)
                btnBahaya?.setTextColor(palette.buttonText)
                btnBahaya?.setOnClickListener {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Buka Pengaturan WiFi di HP Anda", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }



        val cardBssid = findViewById<View>(R.id.cardBssid)
        val cardTls = findViewById<View>(R.id.cardTls)
        val cardSignal = findViewById<View>(R.id.cardSignal)
        val cardLatency = findViewById<View>(R.id.cardLatency)
        val cardConfig = findViewById<View>(R.id.cardConfig)

        if (cardBssid != null && cardTls != null && cardSignal != null && cardLatency != null && cardConfig != null) {
            bindSignalCard(
                cardBssid,
                "Alamat WiFi",
                R.drawable.ic_card_bssid,
                result.bssidChangeScore,
                "BSSID dikenal",
                "BSSID baru, belum dikenal",
                palette
            )
            bindSignalCard(
                cardTls,
                "Sertifikat situs",
                R.drawable.ic_card_tls,
                result.tlsChangeScore,
                "Pin cocok",
                "Sertifikat tidak cocok",
                palette
            )
            bindSignalCard(
                cardSignal,
                "Sinyal",
                R.drawable.ic_card_signal,
                result.signalInstabilityScore,
                signalSummary(result.rssi, result.signalInstabilityScore),
                signalSummary(result.rssi, result.signalInstabilityScore),
                palette
            )
            bindSignalCard(
                cardLatency,
                "Latency",
                R.drawable.ic_card_latency,
                result.latencyDeviationScore,
                latencySummary(result.latencyMs, result.latencyDeviationScore),
                latencySummary(result.latencyMs, result.latencyDeviationScore),
                palette
            )
            bindSignalCard(
                cardConfig,
                "Lalu lintas data",
                R.drawable.ic_card_network,
                result.configChangeScore,
                "Gateway dan DNS sama",
                "Gateway dan DNS berubah",
                palette
            )

            val cardList = listOf(
                cardBssid to result.bssidChangeScore,
                cardTls to result.tlsChangeScore,
                cardSignal to result.signalInstabilityScore,
                cardLatency to result.latencyDeviationScore,
                cardConfig to result.configChangeScore
            )

            val sortedCards = cardList.sortedByDescending { it.second >= 35 }
            val container = findViewById<LinearLayout>(R.id.containerSignalCards)
            if (container != null) {
                container.removeAllViews()
                for ((cardView, _) in sortedCards) {
                    container.addView(cardView)
                }
            }
        }
    }

    private fun bindSignalCard(
        cardRoot: View,
        label: String,
        iconRes: Int,
        score: Int,
        normalSubtext: String,
        abnormalSubtext: String,
        palette: StatusPalette
    ) {
        cardRoot.findViewById<TextView>(R.id.textCardLabel)?.text = label
        cardRoot.findViewById<ImageView>(R.id.iconCardLeading)?.setImageResource(iconRes)

        val accentBar = cardRoot.findViewById<View>(R.id.accentBar)
        val tileContainer = cardRoot.findViewById<View>(R.id.tileIconContainer)
        val leadingIcon = cardRoot.findViewById<ImageView>(R.id.iconCardLeading)
        val subDetail = cardRoot.findViewById<TextView>(R.id.textCardSubDetail)
        val statusChip = cardRoot.findViewById<View>(R.id.layoutCardStatusChip)
        val statusText = cardRoot.findViewById<TextView>(R.id.textCardStatus)
        val statusIcon = cardRoot.findViewById<ImageView>(R.id.iconCardStatus)

        val materialCard = cardRoot as? com.google.android.material.card.MaterialCardView

        val density = resources.displayMetrics.density

        val tileDrawable = GradientDrawable().apply {
            cornerRadius = 12f * density
        }

        val chipDrawable = GradientDrawable().apply {
            cornerRadius = 20f * density
        }

        if (score < 35) {
            materialCard?.setCardBackgroundColor(palette.surface)
            materialCard?.strokeColor = palette.borderNormal
            materialCard?.strokeWidth = (1f * density).toInt()

            accentBar?.visibility = View.GONE

            tileDrawable.setColor(palette.tileNormal)
            tileContainer?.background = tileDrawable
            leadingIcon?.setColorFilter(palette.normalTileIcon)

            subDetail?.text = normalSubtext
            subDetail?.setTextColor(palette.secondary)

            chipDrawable.setColor(palette.normalChipBg)
            statusChip?.background = chipDrawable

            statusText?.text = "Normal"
            statusText?.setTextColor(palette.normalChipText)
            statusIcon?.setImageResource(R.drawable.ic_status_normal)
            statusIcon?.setColorFilter(palette.normalChipText)
        } else {
            accentBar?.visibility = View.VISIBLE
            accentBar?.setBackgroundColor(palette.accent)
            materialCard?.setCardBackgroundColor(palette.surface)
            materialCard?.strokeColor = androidx.core.graphics.ColorUtils.setAlphaComponent(palette.accent, 140)

            tileDrawable.setColor(palette.chipBg)
            tileContainer?.background = tileDrawable
            leadingIcon?.setColorFilter(palette.accent)

            subDetail?.text = abnormalSubtext
            subDetail?.setTextColor(palette.textAccent)

            chipDrawable.setColor(palette.chipBg)
            statusChip?.background = chipDrawable

            statusText?.text = "Berubah"
            statusText?.setTextColor(palette.textAccent)
            statusIcon?.setImageResource(R.drawable.ic_status_changed)
            statusIcon?.setColorFilter(palette.textAccent)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkLocationPermissions() {
        val fineCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineCheck != PackageManager.PERMISSION_GRANTED || coarseCheck != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun setupThreatEvaluator() {
        val (evaluator, monitor) = ThreatEvaluatorFactory.create(applicationContext, if (::tlsPinningMonitor.isInitialized) tlsPinningMonitor else null)
        this.threatEvaluator = evaluator
        this.tlsPinningMonitor = monitor
    }

    private fun openDetailScreen() {
        val result = latestResult ?: return
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_SSID, result.currentSsid ?: "Tidak diketahui")
            putExtra(DetailActivity.EXTRA_BSSID, result.currentBssid ?: "")
            putExtra(DetailActivity.EXTRA_GATEWAY, result.gatewayIp ?: "")
            putExtra(DetailActivity.EXTRA_DNS, result.dnsServers ?: "")
            putExtra(DetailActivity.EXTRA_THREAT_LEVEL, result.threatLevel)
            putExtra(DetailActivity.EXTRA_BSSID_SCORE, result.bssidChangeScore)
            putExtra(DetailActivity.EXTRA_TLS_SCORE, result.tlsChangeScore)
            putExtra(DetailActivity.EXTRA_SIGNAL_SCORE, result.signalInstabilityScore)
            putExtra(DetailActivity.EXTRA_LATENCY_SCORE, result.latencyDeviationScore)
            putExtra(DetailActivity.EXTRA_CONFIG_SCORE, result.configChangeScore)
            putExtra(DetailActivity.EXTRA_BSSID_DETAIL, result.bssidDetail)
            putExtra(DetailActivity.EXTRA_TLS_DETAIL, result.tlsDetail)
            putExtra(DetailActivity.EXTRA_SIGNAL_DETAIL, result.signalDetail)
            putExtra(DetailActivity.EXTRA_LATENCY_DETAIL, result.latencyDetail)
            putExtra(DetailActivity.EXTRA_CONFIG_DETAIL, result.configDetail)
            putExtra(DetailActivity.EXTRA_SECURITY_TYPE, result.securityType.name)
            putExtra(DetailActivity.EXTRA_SECURITY_LABEL, result.securityLabel)
            putExtra(DetailActivity.EXTRA_SECURITY_ADVICE, result.securityAdvice)
            putExtra(DetailActivity.EXTRA_UPDATED_AT, lastUpdatedMillis)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        if (::tlsPinningMonitor.isInitialized) {
            tlsPinningMonitor.stop()
        }
        super.onDestroy()
    }

    companion object {
        fun signalSummary(rssi: Int, score: Int): String {
            return when {
                score == 0 -> "$rssi dBm · stabil"
                score < 35 -> "$rssi dBm · sedikit berubah"
                else -> "$rssi dBm · naik-turun"
            }
        }

        fun latencySummary(latencyMs: Long, score: Int): String {
            if (latencyMs < 0) return "Tidak dapat diukur"
            return when {
                score == 0 -> "$latencyMs ms · stabil"
                score < 35 -> "$latencyMs ms · sedikit berubah"
                else -> "$latencyMs ms · naik-turun"
            }
        }
    }
}
