package com.at.shield

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startService(Intent(this, ShieldVpn::class.java))
            Toast.makeText(this, "تم تشغيل خدمة DNS للحماية", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "لم يتم منح إذن VPN؛ لم يبدأ الفلتر", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF1FD1B0),
                secondary = androidx.compose.ui.graphics.Color(0xFFFFC857),
                background = androidx.compose.ui.graphics.Color(0xFF07131F),
                surface = androidx.compose.ui.graphics.Color(0xFF102234)
            )) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ShieldHome(
                        context = this,
                        onVpn = {
                            val intent = VpnService.prepare(this)
                            if (intent == null) startService(Intent(this, ShieldVpn::class.java))
                            else vpnConsent.launch(intent)
                        },
                        onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onAdmin = { requestAdmin() }
                    )
                }
            }
        }
    }

    private fun requestAdmin() {
        val component = ComponentName(this, AdminRcv::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "يستخدم AT Shield صلاحية مسؤول الجهاز لقفل الشاشة عند الطلب.")
        }
        startActivity(intent)
    }
}

@Composable
private fun ShieldHome(
    context: Context,
    onVpn: () -> Unit,
    onAccessibility: () -> Unit,
    onAdmin: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("أنشئ رمز PIN لتفعيل إعدادات الحماية.") }
    var enabled by remember { mutableStateOf(Store.enabled(context)) }
    var domain by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("AT SHIELD", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text("حماية DNS وأدوات للمساعدة على تجنّب المواقع غير المرغوبة.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (enabled) "الحماية مفعّلة" else "الحماية متوقفة", style = MaterialTheme.typography.titleLarge)
                Text(message)
                if (!Store.hasPin(context)) {
                    OutlinedTextField(
                        value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(12) },
                        label = { Text("إنشاء رمز PIN (4 أرقام أو أكثر)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        if (newPin.length < 4) message = "اكتب 4 أرقام على الأقل."
                        else {
                            Store.setPin(context, newPin)
                            newPin = ""
                            message = "تم حفظ الرمز على هذا الجهاز."
                            refresh++
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الرمز") }
                } else {
                    OutlinedTextField(
                        value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                        label = { Text("رمز PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (Store.checkPin(context, pin)) {
                                message = "تم التحقق من الرمز."
                                pin = ""
                            } else {
                                message = if (Store.lockLeft(context) > 0) "محاولات كثيرة. انتظر 30 دقيقة." else "الرمز غير صحيح."
                                pin = ""
                            }
                        }, modifier = Modifier.weight(1f)) { Text("التحقق") }
                        OutlinedButton(onClick = {
                            if (Store.checkPin(context, pin)) {
                                Store.setEnabled(context, !Store.enabled(context))
                                enabled = Store.enabled(context)
                                DnsLock.apply(context)
                                message = if (enabled) "تم تفعيل إعداد الحماية المحلي." else "تم إيقاف إعداد الحماية المحلي."
                                pin = ""
                            } else message = "أدخل الرمز الصحيح أولاً."
                        }, modifier = Modifier.weight(1f)) { Text(if (enabled) "إيقاف الحماية" else "تفعيل الحماية") }
                    }
                }
            }
        }
        Text("إعدادات التشغيل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Button(onClick = onVpn, modifier = Modifier.fillMaxWidth()) { Text("السماح بتشغيل VPN للحماية") }
        OutlinedButton(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("إعداد خدمة إمكانية الوصول") }
        OutlinedButton(onClick = onAdmin, modifier = Modifier.fillMaxWidth()) { Text("طلب صلاحية مسؤول الجهاز") }
        Divider()
        Text("قائمة الحجب المخصصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text("اسم النطاق، مثل example.com") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (domain.isBlank()) message = "أدخل اسم نطاق أولاً."
            else {
                Store.addCustom(context, domain)
                domain = ""
                message = "تمت إضافة النطاق إلى القائمة المحلية."
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("إضافة نطاق") }
        Text("ملاحظة: لا تمنح هذه النسخة صلاحية Device Owner تلقائياً. كما أن فلترة DNS لا تضمن حجب كل المحتوى، وقد لا تعمل مع كل أنواع الاتصال أو DNS المشفّر.")
    }
}
