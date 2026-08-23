package com.betteruc.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SwatRosterParserTest {
    @Test
    void parsesHeaderAndMemberRolesFromSfInfoAll() {
        SwatRosterParser.Header header = SwatRosterParser.parseHeader(
                "23:23:40 ==== Mitglieder von SWAT (11/13) ===="
        );
        assertEquals(11, header.memberCount());
        assertEquals(13, header.slotLimit());

        assertEquals(
                List.of(new SwatRosterParser.Member("36Flo", 6, "leader")),
                SwatRosterParser.parseMemberRow("- 6 | 36Flo [L]")
        );
        assertEquals(
                List.of(
                        new SwatRosterParser.Member("21Nici_", 4, "member"),
                        new SwatRosterParser.Member("FABI1441", 4, "supervisor"),
                        new SwatRosterParser.Member("mteii", 4, "member")
                ),
                SwatRosterParser.parseMemberRow("- 4 | 21Nici_, FABI1441 [S], mteii")
        );
    }

    @Test
    void ignoresUnrelatedChatMessages() {
        assertNull(SwatRosterParser.parseHeader("Polizei FABI1441 sagt: Mitglieder von SWAT sind cool"));
        assertEquals(List.of(), SwatRosterParser.parseMemberRow("Polizei FABI1441: 4 | FABI1441 [S]"));
    }
}
