package com.at.shield

object Shield {
    val domainKeywords = listOf("porn", "xvideos", "xnxx", "xxx", "hentai", "onlyfans", "redtube", "xhamster", "brazzers", "youporn", "pornhub", "sexcam", "livejasmin", "stripchat", "chaturbate", "casino", "betting", "1xbet", "bet365")
    val doh = listOf("dns.google", "cloudflare-dns.com", "one.one.one.one", "dns.quad9.net", "doh.opendns.com", "dns.adguard.com", "mozilla.cloudflare-dns.com", "doh.cleanbrowsing.org", "dns.nextdns.io")
    val screenKeywords = listOf("porn", "xxx", "hentai", "xvideos", "pornhub", "onlyfans", "سكس", "اباحي", "إباحي", "افلام جنسية", "نيك")
    fun blocked(host: String, custom: Set<String>): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h.isEmpty()) return false
        if (domainKeywords.any { h.contains(it) }) return true
        if (doh.any { h == it || h.endsWith(".$it") }) return true
        return custom.any { h == it || h.endsWith(".$it") }
    }
}