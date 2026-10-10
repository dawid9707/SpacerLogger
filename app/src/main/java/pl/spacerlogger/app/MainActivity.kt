package pl.spacerlogger.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var track: Polyline
    private lateinit var histTrack: Polyline
    private lateinit var myLoc: MyLocationNewOverlay
    private lateinit var tvDistance: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton

    private val ui = Handler(Looper.getMainLooper())
    private var drawnVersion = -1
    private val ticker = object : Runnable {
        override fun run() { render(); ui.postDelayed(this, 1000) }
    }
    private val listener: () -> Unit = { render() }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasFine()) {
                enableMyLocation()
                beginWalk()
            } else {
                toast(getString(R.string.need_location))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        setContentView(R.layout.activity_main)

        // Android 15+ wymusza edge-to-edge – dopasuj układ do pasków systemowych
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        tvDistance = findViewById(R.id.tvDistance)
        tvTime = findViewById(R.id.tvTime)
        tvCalories = findViewById(R.id.tvCalories)
        tvSpeed = findViewById(R.id.tvSpeed)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)
        map.controller.setCenter(GeoPoint(52.2297, 21.0122))   // start: Warszawa, dopóki nie ma GPS

        histTrack = Polyline(map).apply {
            outlinePaint.color = Color.rgb(25, 118, 210)
            outlinePaint.strokeWidth = 12f
        }
        track = Polyline(map).apply {
            outlinePaint.color = Color.rgb(46, 125, 50)
            outlinePaint.strokeWidth = 14f
        }
        myLoc = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        map.overlays.add(histTrack)
        map.overlays.add(track)
        map.overlays.add(myLoc)

        Tracker.weightKg = getSharedPreferences("settings", MODE_PRIVATE).getFloat("weight", 70f).toDouble()

        btnStart.setOnClickListener {
            when (Tracker.state) {
                WalkState.IDLE -> requestAndStart()
                WalkState.RUNNING -> TrackingService.send(this, TrackingService.ACTION_PAUSE)
                WalkState.PAUSED -> TrackingService.send(this, TrackingService.ACTION_RESUME)
            }
        }
        btnStop.setOnClickListener { stopWalk() }
        findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener { showHistory() }
        findViewById<MaterialButton>(R.id.btnWeight).setOnClickListener { showWeightDialog() }
        findViewById<MaterialButton>(R.id.btnCenter).setOnClickListener {
            if (hasFine()) { enableMyLocation(); myLoc.enableFollowLocation() }
        }
    }

    override fun onStart() {
        super.onStart()
        Tracker.addListener(listener)
        ui.post(ticker)
    }

    override fun onStop() {
        ui.removeCallbacks(ticker)
        Tracker.removeListener(listener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        if (hasFine()) {
            enableMyLocation()
            myLoc.enableFollowLocation()
        }
    }

    override fun onPause() {
        myLoc.disableMyLocation()
        map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        map.onDetach()
        super.onDestroy()
    }

    // ---------- logika spaceru ----------

    private fun hasFine() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun enableMyLocation() {
        if (!myLoc.isMyLocationEnabled) myLoc.enableMyLocation()
    }

    private fun requestAndStart() {
        if (!hasFine()) {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            permLauncher.launch(perms.toTypedArray())
            return
        }
        beginWalk()
    }

    private fun beginWalk() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            toast(getString(R.string.enable_gps))
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        histTrack.setPoints(emptyList())
        myLoc.enableFollowLocation()
        TrackingService.send(this, TrackingService.ACTION_START)
    }

    private fun stopWalk() {
        if (Tracker.state == WalkState.IDLE) return
        val walk = Tracker.snapshot()
        if (walk.distanceM >= 20.0) {
            WalkStore.add(this, walk)
            toast(getString(R.string.saved, summary(walk)))
        } else {
            toast(getString(R.string.too_short))
        }
        TrackingService.send(this, TrackingService.ACTION_STOP)
    }

    private fun summary(w: Walk) = String.format(
        Locale.getDefault(), "%.2f km · %s · %.0f kcal",
        w.distanceM / 1000.0, formatTime(w.durationMs), w.calories
    )

    // ---------- UI ----------

    private fun render() {
        val d = Tracker.distanceM
        val ms = Tracker.elapsedMs()
        tvDistance.text = String.format(Locale.getDefault(), "%.2f km", d / 1000.0)
        tvTime.text = formatTime(ms)
        tvCalories.text = String.format(Locale.getDefault(), "%.0f kcal", Tracker.calories)
        val hours = ms / 3_600_000.0
        tvSpeed.text = String.format(
            Locale.getDefault(), "%.1f km/h", if (hours > 0 && d > 0) d / 1000.0 / hours else 0.0
        )

        btnStart.setText(
            when (Tracker.state) {
                WalkState.IDLE -> R.string.start
                WalkState.RUNNING -> R.string.pause
                WalkState.PAUSED -> R.string.resume
            }
        )
        btnStop.isEnabled = Tracker.state != WalkState.IDLE

        if (drawnVersion != Tracker.version) {
            drawnVersion = Tracker.version
            track.setPoints(Tracker.points.map { GeoPoint(it.lat, it.lon) })
            map.invalidate()
        }
    }

    private fun showHistory() {
        val walks = WalkStore.load(this).sortedByDescending { it.startedAt }
        if (walks.isEmpty()) { toast(getString(R.string.no_history)); return }
        val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val items = walks.map { "${df.format(Date(it.startedAt))}\n${summary(it)}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.history)
            .setItems(items) { _, i -> showWalk(walks[i]) }
            .setNeutralButton(R.string.clear) { _, _ ->
                WalkStore.clear(this)
                histTrack.setPoints(emptyList())
                map.invalidate()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showWalk(w: Walk) {
        val pts = w.points.map { GeoPoint(it.lat, it.lon) }
        histTrack.setPoints(pts)
        myLoc.disableFollowLocation()
        if (pts.size >= 2) {
            map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), true, 100) }
        }
        map.invalidate()
    }

    private fun showWeightDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.1f", Tracker.weightKg))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.weight_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val v = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (v == null || v < 30 || v > 250) {
                    toast(getString(R.string.invalid_weight))
                } else {
                    Tracker.weightKg = v
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putFloat("weight", v.toFloat()).apply()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
