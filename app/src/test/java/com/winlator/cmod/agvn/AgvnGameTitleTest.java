package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnGameTitleTest {
    @Test
    public void folderNamesBecomeReadableTitles() {
        assertEquals("A New Massage Shop", AgvnGameTitle.pretty("A-NEW-MASSAGE-SHOP-GAMEHUB"));
        assertEquals("Elf Sex Farm", AgvnGameTitle.pretty("ElfSexFarm-GameHub"));
        assertEquals("Succubus Successor (siêu nhẹ)", AgvnGameTitle.pretty("SUCCUBUS-SUCCESSOR-SIEU-NHE"));
        assertEquals("Massage My Ex Bully v118", AgvnGameTitle.pretty("MASSAGE-MY-EX-BULLY-V118-GAMEHUB"));
        assertEquals("Memeris Pervy RPG", AgvnGameTitle.pretty("MEMERIS-PERVY-RPG-CHEAT-AGVN"));
        assertEquals("Trang Thai Bat Thuong III", AgvnGameTitle.pretty("TRANG-THAI-BAT-THUONG-III"));
        assertEquals("Legend Cleaner Lite", AgvnGameTitle.pretty("LEGEND CLEANER GAMEHUB LITE"));
        assertEquals("My Game", AgvnGameTitle.pretty("my_game"));
        assertEquals("Karryn's Prison", AgvnGameTitle.pretty("Karryn's Prison [JoiPlay] [Viet-Hoa]"));
    }

    @Test
    public void onlyTagsFallsBackToFolderName() {
        assertEquals("AGVN", AgvnGameTitle.pretty(" AGVN "));
        assertEquals("", AgvnGameTitle.pretty(null));
    }

    @Test
    public void searchIgnoresAccentsAndCase() {
        assertEquals("duong ve nha", AgvnGameTitle.searchKey("Đường Về Nhà"));
        assertTrue(AgvnGameTitle.matches("Trạng Thái Bất Thường III", "trang thai"));
        assertTrue(AgvnGameTitle.matches("Elf Sex Farm", "  farm ELF "));
        assertFalse(AgvnGameTitle.matches("Elf Sex Farm", "farm zz"));
        assertTrue(AgvnGameTitle.matches("anything", ""));
    }
}
