package pl.grillhub.staff

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

private val Red = Color(0xFFE11E26)
private val Ink = Color(0xFF050607)
private val Bg = Color(0xFFF6F6F6)
private val Green = Color(0xFF1A9F4B)

enum class Screen { Login, Home, Scan, Phone, Customer, Redeem }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        L.lang = store.lang
        if (store.baseUrl.isNotBlank()) Api.baseUrl = store.baseUrl
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Red, onPrimary = Color.White)) {
                val dir = if (L.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    StaffApp(store)
                }
            }
        }
    }
}

@Composable
fun StaffApp(store: Store) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf(store.pin) }
    var screen by remember { mutableStateOf(if (store.pin.isBlank()) Screen.Login else Screen.Home) }
    var customer by remember { mutableStateOf<Customer?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stampResult by remember { mutableStateOf<StampResult?>(null) }
    var redeemResult by remember { mutableStateOf<RedeemResult?>(null) }
    var scanNonce by remember { mutableStateOf(0) }

    fun go(s: Screen) { error = null; stampResult = null; redeemResult = null; screen = s }

    fun lookup(q: String, fromToken: Boolean, backTo: Screen) {
        scope.launch {
            busy = true; error = null
            try {
                val c = Api.find(pin, q)
                customer = c
                token = if (fromToken) q else null
                go(Screen.Customer)
            } catch (e: Throwable) {
                val msg = L.error(e)
                go(backTo)
                error = msg
                scanNonce++ // restart the camera so the next QR can be scanned
            }
            busy = false
        }
    }

    BackHandler(enabled = screen != Screen.Login && screen != Screen.Home) { go(Screen.Home) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when (screen) {
            Screen.Login -> LoginScreen(error, busy) { entered ->
                scope.launch {
                    busy = true; error = null
                    try {
                        if (Api.verifyPin(entered)) {
                            pin = entered; store.pin = entered; go(Screen.Home)
                        } else error = L.t("wrongPin")
                    } catch (e: Throwable) { error = L.error(e) }
                    busy = false
                }
            }

            Screen.Home -> HomeScreen(
                onScan = { go(Screen.Scan) },
                onPhone = { go(Screen.Phone) },
                onRedeem = { go(Screen.Redeem) },
                onLogout = { store.pin = ""; pin = ""; go(Screen.Login) },
                onLang = { L.lang = it; store.lang = it }
            )

            Screen.Scan -> ScanScreen(error, scanNonce, onCode = { raw -> lookup(parseQr(raw), true, Screen.Scan) }, onBack = { go(Screen.Home) })

            Screen.Phone -> PhoneScreen(error, busy,
                onFind = { lookup(it.trim(), false, Screen.Phone) },
                onBack = { go(Screen.Home) })

            Screen.Customer -> customer?.let { c ->
                CustomerScreen(c, stampResult, error, busy,
                    onStamp = { programId ->
                        scope.launch {
                            busy = true; error = null
                            try {
                                val r = Api.stamp(pin, c.phone, token, programId)
                                stampResult = r
                                customer = Api.find(pin, token ?: c.phone)
                            } catch (e: Throwable) { error = L.error(e) }
                            busy = false
                        }
                    },
                    onHome = { go(Screen.Home) })
            }

            Screen.Redeem -> RedeemScreen(redeemResult, error, busy,
                onRedeem = { code ->
                    scope.launch {
                        busy = true; error = null; redeemResult = null
                        try { redeemResult = Api.redeem(pin, code.trim().uppercase()) }
                        catch (e: Throwable) { error = L.error(e) }
                        busy = false
                    }
                },
                onBack = { go(Screen.Home) })
        }
    }
}

fun parseQr(raw: String): String = try {
    Uri.parse(raw.trim()).getQueryParameter("card") ?: raw.trim()
} catch (e: Exception) { raw.trim() }

/* ---------- shared pieces ---------- */

@Composable
fun Logo(size: Int = 130) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "Grill Hub",
        modifier = Modifier.size(size.dp)
    )
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    if (primary) {
        Button(
            onClick = onClick, enabled = enabled, shape = shape,
            modifier = Modifier.fillMaxWidth().height(68.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)
        ) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = onClick, enabled = enabled, shape = shape,
            modifier = Modifier.fillMaxWidth().height(68.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink)
        ) { Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ErrorBox(msg: String?) {
    if (msg != null) {
        Text(
            msg, color = Color(0xFFA1121A), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFDE8E9)).padding(12.dp)
        )
    }
}

@Composable
fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) { content() }
}

/* ---------- screens ---------- */

@Composable
fun LoginScreen(error: String?, busy: Boolean, onLogin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Page {
        Spacer(Modifier.height(24.dp))
        Logo(170)
        Text("Staff", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
        OutlinedTextField(
            value = pin, onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("PIN", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        BigButton(L.t("login"), enabled = !busy) {
            if (pin.length in 4..8) onLogin(pin)
        }
        ErrorBox(error)
    }
}

@Composable
fun HomeScreen(
    onScan: () -> Unit, onPhone: () -> Unit, onRedeem: () -> Unit,
    onLogout: () -> Unit, onLang: (String) -> Unit
) {
    Page {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onLogout) { Text(L.t("logout"), color = Color.Gray) }
            Row {
                listOf("ar" to "AR", "pl" to "PL", "en" to "EN").forEach { (code, label) ->
                    TextButton(onClick = { onLang(code) }) {
                        Text(label, color = if (L.lang == code) Red else Color.Gray,
                            fontWeight = if (L.lang == code) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Logo(150)
        Spacer(Modifier.height(8.dp))
        BigButton("📷  " + L.t("scan"), onClick = onScan)
        BigButton("📞  " + L.t("byPhone"), primary = false, onClick = onPhone)
        BigButton("🎁  " + L.t("redeem"), primary = false, onClick = onRedeem)
    }
}

@Composable
fun ScanScreen(error: String?, nonce: Int, onCode: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Page {
        Text(L.t("scanTitle"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
        if (granted) {
            // key(nonce) restarts the scanner after a failed lookup so the user can retry
            key(nonce) {
                CameraPreview(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)), onCode)
            }
        } else {
            Text(L.t("camPerm"), textAlign = TextAlign.Center)
            BigButton(L.t("allow")) { launcher.launch(Manifest.permission.CAMERA) }
        }
        ErrorBox(error)
        BigButton(L.t("back"), primary = false, onClick = onBack)
    }
}

@Composable
fun CameraPreview(modifier: Modifier, onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    AndroidView(modifier = modifier, factory = { c ->
        val previewView = PreviewView(c).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        val future = ProcessCameraProvider.getInstance(c)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(ContextCompat.getMainExecutor(c), QrAnalyzer(onCode))
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(c))
        previewView
    })
}

class QrAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    @Volatile private var done = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || done) { proxy.close(); return }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { list ->
                val v = list.firstOrNull()?.rawValue
                if (v != null && !done) { done = true; onResult(v) }
            }
            .addOnCompleteListener { proxy.close() }
    }
}

@Composable
fun PhoneScreen(error: String?, busy: Boolean, onFind: (String) -> Unit, onBack: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    Page {
        Text(L.t("phoneTitle"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        OutlinedTextField(
            value = phone, onValueChange = { phone = it },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("+48500111222", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        BigButton(L.t("find"), enabled = !busy && phone.isNotBlank()) { onFind(phone) }
        ErrorBox(error)
        BigButton(L.t("back"), primary = false, onClick = onBack)
    }
}

@Composable
fun CustomerScreen(
    c: Customer, result: StampResult?, error: String?, busy: Boolean,
    onStamp: (Int) -> Unit, onHome: () -> Unit
) {
    Page {
        Text(c.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(c.phone, color = Color.Gray)

        result?.let { r ->
            Text("✅ " + L.t("stamped") + " — ${r.stamps}", color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (r.completed && r.rewardCode != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉 " + L.t("complete"), color = Color.White, textAlign = TextAlign.Center)
                    Text(r.rewardCode, color = Color(0xFFFFD24A), fontSize = 40.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(vertical = 10.dp))
                    Text(r.rewardTitle ?: "", color = Color.White, textAlign = TextAlign.Center)
                }
            }
        }
        ErrorBox(error)

        val haveIds = c.cards.map { it.programId }.toSet()
        c.cards.forEach { card ->
            CardBox(card.programName, card.stamps, card.required, card.rewardTitle, busy) { onStamp(card.programId) }
        }
        c.programs.filter { it.id !in haveIds }.forEach { p ->
            CardBox(p.name + " (" + L.t("newCard") + ")", 0, 0, "", busy) { onStamp(p.id) }
        }
        BigButton(L.t("home"), primary = false, onClick = onHome)
    }
}

@Composable
fun CardBox(name: String, stamps: Int, required: Int, reward: String, busy: Boolean, onStamp: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        if (required > 0) {
            LinearProgressIndicator(
                progress = { (stamps.toFloat() / required).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)),
                color = Red, trackColor = Color(0xFFEEEEEE)
            )
            Text("$stamps/$required ${L.t("stamps")}" + if (reward.isNotBlank()) " — $reward" else "", color = Color.Gray)
        }
        BigButton("➕  " + L.t("addStamp"), enabled = !busy, onClick = onStamp)
    }
}

@Composable
fun RedeemScreen(
    result: RedeemResult?, error: String?, busy: Boolean,
    onRedeem: (String) -> Unit, onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    Page {
        Text(L.t("codeTitle"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        OutlinedTextField(
            value = code, onValueChange = { code = it.uppercase() },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("GH-XXXXXX", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center)
        )
        BigButton(L.t("confirm"), enabled = !busy && code.isNotBlank()) { onRedeem(code) }
        result?.let {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✅ " + L.t("redeemed"), color = Green, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(it.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(it.customer, color = Color.Gray)
            }
        }
        ErrorBox(error)
        BigButton(L.t("back"), primary = false, onClick = onBack)
    }
}
