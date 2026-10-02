package ca.schippers.hfm.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CR-02: addresses derived from the published test vectors of BIP84 and BIP44 (the wallet made
 * from the mnemonic "abandon abandon ... about"). Never a private key.
 */
class BitcoinTest {

    private val zpub = "zpub6rFR7y4Q2AijBEqTUquhVz398htDFrtymD9xYYfG1m4wAcvPhXNfE3EfH1r1ADqtfSdVCToUG868RvUUkgDKf31mGDtKsAYz2oz2AGutZYs"
    private val xpub = "xpub6BosfCnifzxcFwrSzQiqu2DBVTshkCXacvNsWGYJVVhhawA7d4R5WSWGFNbi8Aw6ZRc1brxMyWMzG3DSSSSoekkudhUd9yLb6qx39T9nMdj"

    @Test
    fun `BIP84 native SegWit addresses`() {
        val key = Bitcoin.parseExtendedKey(zpub)
        assertEquals(Bitcoin.AddressType.P2WPKH, key.type)
        assertEquals("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", key.address(0, 0))
        assertEquals("bc1qnjg0jd8228aq7egyzacy8cys3knf9xvrerkf9g", key.address(0, 1))
        assertEquals("bc1q8c6fshw2dlwun7ekn9qwf37cu2rn755upcp6el", key.address(1, 0))
    }

    @Test
    fun `BIP44 legacy addresses`() {
        val key = Bitcoin.parseExtendedKey(xpub)
        assertEquals("1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA", key.address(0, 0))
    }

    @Test
    fun `addresses are checked for typing mistakes`() {
        assertTrue(Bitcoin.isValidAddress("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"))
        assertFalse(Bitcoin.isValidAddress("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyw"), "one character changed")
        assertTrue(Bitcoin.isValidAddress("1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA"))
        assertFalse(Bitcoin.isValidAddress("1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabB"))
        assertTrue(Bitcoin.isValidAddress("bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297"), "Taproot (BIP86 vector)")
        assertFalse(Bitcoin.isValidAddress("not an address"))
    }

    @Test
    fun `private keys and broken keys are refused`() {
        assertFailsWith<Bitcoin.InvalidKeyException> {
            Bitcoin.parseExtendedKey("xprv9s21ZrQH143K3QTDL4LXw2F7HEK3wJUD2nW2nRk4stbPy6cq3jPPqjiChkVvvNKmPGJxWUtg6LnF5kejMRNNU3TGtRBeJgk33yuGBxrMPHi")
        }
        assertFailsWith<Bitcoin.InvalidKeyException> { Bitcoin.parseExtendedKey(zpub.dropLast(1) + "t") }
    }
}
