package com.at.shield

import android.net.VpnService
import android.content.Intent
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class ShieldVpn : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private val pool = Executors.newCachedThreadPool()
    private val upstream = InetAddress.getByName("1.1.1.3")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) start()
        return START_STICKY
    }
    private fun start() {
        tun = Builder().setSession("AT Shield").addAddress("10.0.0.2", 32).addDnsServer("10.0.0.1").addRoute("10.0.0.1", 32).establish() ?: return
        running = true
        Thread { loop() }.start()
    }
    private fun loop() {
        val input = FileInputStream(tun!!.fileDescriptor)
        val output = FileOutputStream(tun!!.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val n = try { input.read(buf) } catch (e: Exception) { break }
            if (n <= 0) continue
            val pkt = buf.copyOf(n)
            pool.execute { handle(pkt, output) }
        }
    }
    private fun handle(pkt: ByteArray, out: FileOutputStream) {
        try {
            if ((pkt[0].toInt() shr 4) != 4) return
            val ihl = (pkt[0].toInt() and 0x0F) * 4
            if (pkt[9].toInt() != 17) return
            val dport = ((pkt[ihl + 2].toInt() and 255) shl 8) or (pkt[ihl + 3].toInt() and 255)
            if (dport != 53) return
            val dns = pkt.copyOfRange(ihl + 8, pkt.size)
            val (host, qEnd) = parseQuestion(dns) ?: return
            val resp: ByteArray = if (Shield.blocked(host, Store.custom(this))) {
                val r = dns.copyOf(qEnd)
                r[2] = 0x81.toByte(); r[3] = 0x83.toByte()
                r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0
                r
            } else forward(dns) ?: return
            val reply = buildReply(pkt, ihl, resp)
            synchronized(out) { out.write(reply) }
        } catch (_: Exception) { }
    }
    private fun parseQuestion(d: ByteArray): Pair<String, Int>? {
        if (d.size < 17) return null
        var i = 12
        val sb = StringBuilder()
        while (i < d.size) {
            val len = d[i].toInt() and 255
            if (len == 0) { i++; break }
            if (len >= 192 || i + len >= d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(d, i + 1, len))
            i += len + 1
        }
        return Pair(sb.toString(), minOf(i + 4, d.size))
    }
    private fun forward(dns: ByteArray): ByteArray? {
        val s = DatagramSocket()
        return try {
            protect(s)
            s.soTimeout = 3000
            s.send(DatagramPacket(dns, dns.size, upstream, 53))
            val b = ByteArray(4096)
            val rp = DatagramPacket(b, b.size)
            s.receive(rp)
            b.copyOf(rp.length)
        } catch (e: Exception) { null } finally { s.close() }
    }
    private fun buildReply(req: ByteArray, ihl: Int, dns: ByteArray): ByteArray {
        val total = ihl + 8 + dns.size
        val o = ByteArray(total)
        System.arraycopy(req, 0, o, 0, ihl + 8)
        System.arraycopy(req, 16, o, 12, 4)
        System.arraycopy(req, 12, o, 16, 4)
        o[2] = (total shr 8).toByte(); o[3] = total.toByte()
        o[10] = 0; o[11] = 0
        var sum = 0
        var i = 0
        while (i < ihl) { sum += ((o[i].toInt() and 255) shl 8) or (o[i + 1].toInt() and 255); i += 2 }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        o[10] = (ck shr 8).toByte(); o[11] = ck.toByte()
        o[ihl] = req[ihl + 2]; o[ihl + 1] = req[ihl + 3]
        o[ihl + 2] = req[ihl]; o[ihl + 3] = req[ihl + 1]
        val ul = 8 + dns.size
        o[ihl + 4] = (ul shr 8).toByte(); o[ihl + 5] = ul.toByte()
        o[ihl + 6] = 0; o[ihl + 7] = 0
        System.arraycopy(dns, 0, o, ihl + 8, dns.size)
        return o
    }
    override fun onRevoke() { running = false; super.onRevoke() }
    override fun onDestroy() {
        running = false
        try { tun?.close() } catch (_: Exception) { }
        super.onDestroy()
    }
}