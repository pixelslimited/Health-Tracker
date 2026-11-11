package ng.pixelslimited.health.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.Food
import ng.pixelslimited.health.data.Foods
import ng.pixelslimited.health.data.T
import java.io.File
import java.io.InputStream
import java.util.Locale

private fun decode(open: () -> InputStream?): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/** Photo of a fruit or dish, labelled on the phone; the person confirms the food and sets the portion. */
@Composable
fun FoodScan(onClose: () -> Unit, onSave: (Entry) -> Unit) {
    val ctx = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var labels by remember { mutableStateOf<List<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var food by remember { mutableStateOf<Food?>(null) }
    var grams by remember { mutableIntStateOf(100) }
    var showAll by remember { mutableStateOf(false) }

    fun analyse(bmp: Bitmap) {
        bitmap = bmp
        busy = true
        labels = null
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
            .process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { l ->
                labels = l.sortedByDescending { it.confidence }.map { it.text }
                busy = false
            }
            .addOnFailureListener {
                labels = emptyList()
                busy = false
            }
    }

    val file = remember { File(ctx.cacheDir, "food/photo.jpg") }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) decode { file.inputStream() }?.let { analyse(it) }
    }
    fun launchCamera() {
        file.parentFile?.mkdirs()
        take.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file))
    }
    val camAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) launchCamera() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) decode { ctx.contentResolver.openInputStream(uri) }?.let { analyse(it) }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(s(R.string.scan_food), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text(s(R.string.close)) }
                }
                val chosen = food
                if (chosen == null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                            else camAsk.launch(Manifest.permission.CAMERA)
                        }, modifier = Modifier.weight(1f)) { Text(s(R.string.take_photo)) }
                        OutlinedButton(onClick = {
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, modifier = Modifier.weight(1f)) { Text(s(R.string.pick_photo)) }
                    }
                    bitmap?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
                    }
                    if (busy) Text(s(R.string.analysing))
                    val found = labels?.let { Foods.match(it) }
                    if (found != null) {
                        if (found.isEmpty()) {
                            Text(s(R.string.no_match))
                        } else {
                            Text(s(R.string.looks_like), style = MaterialTheme.typography.titleMedium)
                            found.take(5).forEach { f ->
                                Button(onClick = { food = f; grams = f.portion }, modifier = Modifier.fillMaxWidth()) { Text(f.name) }
                            }
                        }
                    }
                    OutlinedButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.pick_food)) }
                    if (showAll || (found != null && found.isEmpty())) {
                        Foods.all.forEach { f ->
                            OutlinedButton(onClick = { food = f; grams = f.portion }, modifier = Modifier.fillMaxWidth()) { Text(f.name) }
                        }
                    }
                } else {
                    val k = grams / 100.0
                    val kcal = Math.round(chosen.kcal * k).toInt()
                    val carbs = Math.round(chosen.carbs * k).toInt()
                    val protein = chosen.protein * k
                    val fat = chosen.fat * k
                    Text(chosen.name, style = MaterialTheme.typography.titleLarge)
                    Ruler(grams, { grams = it }, 10..1000, 10, s(R.string.portion_g)) { "$it g" }
                    Stat(s(R.string.calories), ctx.getString(R.string.f_kcal, kcal))
                    Stat(s(R.string.carbs_g), ctx.getString(R.string.f_carbs, carbs))
                    Stat(s(R.string.protein), String.format(Locale.getDefault(), "%.1f g", protein))
                    Stat(s(R.string.fat), String.format(Locale.getDefault(), "%.1f g", fat))
                    Button(onClick = {
                        onSave(Entry(T.MEAL, System.currentTimeMillis(), carbs.toDouble(), kcal.toDouble(), note = ctx.getString(R.string.nutr_note, chosen.name, grams, protein, fat)))
                    }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.save)) }
                    OutlinedButton(onClick = { food = null }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.cancel)) }
                }
                Text(s(R.string.food_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
