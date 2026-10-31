package com.ses.asistan.ses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictationTest {
    @Test fun segmentsAccumulate() {
        assertEquals(Dictation.Step.More("yarın sabah"), Dictation.feed("", "yarın sabah"))
        assertEquals(Dictation.Step.More("yarın sabah lastikleri değiştir"), Dictation.feed("yarın sabah", "lastikleri değiştir"))
        // boş parça (sessizlik) birikeni bozmaz
        assertEquals(Dictation.Step.More("süt al"), Dictation.feed("süt al", null))
    }

    @Test fun repeatedWordAtRestartDropped() {
        assertEquals("süt ekmek peynir", Dictation.join("süt ekmek", "ekmek peynir"))
        assertEquals("süt ekmek", Dictation.join("süt ekmek", "ekmek"))
    }

    @Test fun endWords() {
        assertEquals(Dictation.Step.Done("süt al"), Dictation.feed("süt al", "tamam"))
        assertEquals(Dictation.Step.Done("süt al"), Dictation.feed("süt al", "Bitti."))
        assertEquals(Dictation.Step.Done("süt al ekmek de"), Dictation.feed("süt al", "ekmek de, notu kaydet"))
        // not içindeki "tamam" bitirmez
        assertEquals(Dictation.Step.More("tamam dedi ama gelmedi"), Dictation.feed("", "tamam dedi ama gelmedi"))
        assertEquals(Dictation.Step.Cancel, Dictation.feed("süt al", "vazgeç"))
    }

    @Test fun finishCapitalises() {
        assertEquals("İlaçları al", Dictation.finish("ilaçları al."))
    }

    @Test fun clippedNoteAfterWake() {
        assertEquals("süt", Dictation.clippedNote("al süt"))
        assertEquals("süt", Dictation.clippedNote("t al süt"))
        assertEquals("yarın ara", Dictation.clippedNote("not al yarın ara"))
        assertEquals("", Dictation.clippedNote("al"))
        assertNull(Dictation.clippedNote("almanya'ya git"))
        assertNull(Dictation.clippedNote("eczaneye git"))
    }
}
