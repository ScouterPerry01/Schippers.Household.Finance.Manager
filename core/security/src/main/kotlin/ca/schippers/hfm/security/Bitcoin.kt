package ca.schippers.hfm.security

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Watch-only Bitcoin (CR-02): public addresses from an extended public key, never a private key or
 * a seed phrase. Supports xpub (legacy 1... addresses, BIP44), ypub (wrapped SegWit 3..., BIP49)
 * and zpub (native SegWit bc1q..., BIP84), and checks single addresses for typing mistakes.
 */
object Bitcoin {

    enum class AddressType { P2PKH, P2SH_P2WPKH, P2WPKH }

    class InvalidKeyException(message: String) : IllegalArgumentException(message)

    /** A decoded extended public key: what is needed to derive child keys. */
    class ExtendedKey(val type: AddressType, val publicKey: ByteArray, val chainCode: ByteArray) {

        /** The address at [chain] (0 to receive, 1 for change) and [index], as BIP44/49/84 wallets use them. */
        fun address(chain: Int, index: Int): String {
            val child = derive(derive(this, chain), index)
            return addressOf(child.publicKey, type)
        }
    }

    private val curve = SECNamedCurves.getByName("secp256k1")
    private const val HARDENED = 0x80000000.toInt()

    private val VERSIONS = mapOf(
        0x0488B21E to AddressType.P2PKH, // xpub
        0x049D7CB2 to AddressType.P2SH_P2WPKH, // ypub
        0x04B24746 to AddressType.P2WPKH, // zpub
    )

    /** Reads an xpub, ypub or zpub. Private keys (xprv...) are refused. */
    fun parseExtendedKey(text: String): ExtendedKey {
        val t = text.trim()
        if (t.substring(1, minOf(4, t.length)) == "prv") throw InvalidKeyException("This is a private key: never enter it here")
        val data = Base58.decodeChecked(t) ?: throw InvalidKeyException("Not a valid extended public key")
        if (data.size != 78) throw InvalidKeyException("Not a valid extended public key")
        val version = BigInteger(1, data.copyOfRange(0, 4)).toInt()
        val type = VERSIONS[version] ?: throw InvalidKeyException("Unsupported key type")
        val key = data.copyOfRange(45, 78)
        if (key[0] != 2.toByte() && key[0] != 3.toByte()) throw InvalidKeyException("Not a public key")
        return ExtendedKey(type, key, data.copyOfRange(13, 45))
    }

    /** Public child derivation (BIP32 CKDpub) for a non-hardened index. */
    private fun derive(parent: ExtendedKey, index: Int): ExtendedKey {
        require(index and HARDENED == 0) { "Hardened keys cannot be derived from a public key" }
        val mac = HMac(SHA512Digest()).apply { init(KeyParameter(parent.chainCode)) }
        val data = parent.publicKey + byteArrayOf((index ushr 24).toByte(), (index ushr 16).toByte(), (index ushr 8).toByte(), index.toByte())
        mac.update(data, 0, data.size)
        val out = ByteArray(64).also { mac.doFinal(it, 0) }
        val il = BigInteger(1, out.copyOfRange(0, 32))
        if (il >= curve.n) throw InvalidKeyException("Invalid child key")
        val point = curve.g.multiply(il).add(curve.curve.decodePoint(parent.publicKey)).normalize()
        if (point.isInfinity) throw InvalidKeyException("Invalid child key")
        return ExtendedKey(parent.type, point.getEncoded(true), out.copyOfRange(32, 64))
    }

    fun addressOf(publicKey: ByteArray, type: AddressType): String {
        val h = hash160(publicKey)
        return when (type) {
            AddressType.P2PKH -> Base58.encodeChecked(byteArrayOf(0x00) + h)
            AddressType.P2SH_P2WPKH -> Base58.encodeChecked(byteArrayOf(0x05) + hash160(byteArrayOf(0x00, 0x14) + h))
            AddressType.P2WPKH -> Bech32.encodeSegwit("bc", 0, h)
        }
    }

    /** Whether [text] is a well-formed mainnet address (legacy, wrapped SegWit, native SegWit or Taproot), checksum included. */
    fun isValidAddress(text: String): Boolean {
        val t = text.trim()
        return when {
            t.lowercase().startsWith("bc1") -> Bech32.isValidSegwit(t.lowercase(), "bc")
            t.startsWith("1") || t.startsWith("3") -> Base58.decodeChecked(t)?.let { it.size == 21 && (it[0] == 0x00.toByte() || it[0] == 0x05.toByte()) } == true
            else -> false
        }
    }

    private fun hash160(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256").digest(data)
        val ripe = RIPEMD160Digest()
        ripe.update(sha, 0, sha.size)
        return ByteArray(20).also { ripe.doFinal(it, 0) }
    }
}

internal object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val FIFTY_EIGHT = BigInteger.valueOf(58)

    fun encodeChecked(payload: ByteArray): String = encode(payload + checksum(payload))

    fun decodeChecked(text: String): ByteArray? {
        val raw = decode(text) ?: return null
        if (raw.size < 5) return null
        val payload = raw.copyOfRange(0, raw.size - 4)
        return if (checksum(payload).contentEquals(raw.copyOfRange(raw.size - 4, raw.size))) payload else null
    }

    private fun checksum(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        return sha.digest(sha.digest(data)).copyOfRange(0, 4)
    }

    private fun encode(data: ByteArray): String {
        var n = BigInteger(1, data)
        val sb = StringBuilder()
        while (n.signum() > 0) {
            val (q, r) = n.divideAndRemainder(FIFTY_EIGHT)
            sb.append(ALPHABET[r.toInt()])
            n = q
        }
        data.takeWhile { it == 0.toByte() }.forEach { _ -> sb.append('1') }
        return sb.reverse().toString()
    }

    private fun decode(text: String): ByteArray? {
        var n = BigInteger.ZERO
        for (c in text) {
            val digit = ALPHABET.indexOf(c)
            if (digit < 0) return null
            n = n.multiply(FIFTY_EIGHT).add(BigInteger.valueOf(digit.toLong()))
        }
        val bytes = n.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else if (n.signum() == 0) ByteArray(0) else it }
        val zeros = text.takeWhile { it == '1' }.length
        return ByteArray(zeros) + bytes
    }
}

internal object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32M = 0x2bc830a3

    private fun polymod(values: IntArray): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = (chk and 0x1ffffff shl 5) xor v
            for (i in 0 until 5) if ((top ushr i) and 1 == 1) chk = chk xor gen[i]
        }
        return chk
    }

    private fun expand(hrp: String): IntArray = hrp.map { it.code ushr 5 }.toIntArray() + intArrayOf(0) + hrp.map { it.code and 31 }.toIntArray()

    private fun convertBits(data: ByteArray, from: Int, to: Int, pad: Boolean): IntArray? {
        var acc = 0
        var bits = 0
        val out = ArrayList<Int>()
        val max = (1 shl to) - 1
        for (b in data) {
            val v = b.toInt() and 0xff
            acc = (acc shl from) or v
            bits += from
            while (bits >= to) { bits -= to; out += (acc ushr bits) and max }
        }
        if (pad) { if (bits > 0) out += (acc shl (to - bits)) and max } else if (bits >= from || ((acc shl (to - bits)) and max) != 0) return null
        return out.toIntArray()
    }

    fun encodeSegwit(hrp: String, version: Int, program: ByteArray): String {
        val data = intArrayOf(version) + convertBits(program, 8, 5, true)!!
        val constant = if (version == 0) 1 else BECH32M
        val mod = polymod(expand(hrp) + data + IntArray(6)) xor constant
        val checksum = IntArray(6) { (mod ushr (5 * (5 - it))) and 31 }
        return hrp + "1" + (data + checksum).joinToString("") { CHARSET[it].toString() }
    }

    fun isValidSegwit(address: String, hrp: String): Boolean {
        val pos = address.lastIndexOf('1')
        if (address.substring(0, maxOf(pos, 0)) != hrp || address.length - pos < 7) return false
        val data = address.substring(pos + 1).map { CHARSET.indexOf(it) }.toIntArray()
        if (data.any { it < 0 }) return false
        val check = polymod(expand(hrp) + data)
        val version = data[0]
        return (version == 0 && check == 1) || (version in 1..16 && check == BECH32M)
    }
}
