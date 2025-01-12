package shrx.discordintegration;

import static shrx.discordintegration.DiscordIntegration.DEFECT_EMOJI;
import static shrx.discordintegration.DiscordIntegration.IRONCLAD_EMOJI;
import static shrx.discordintegration.DiscordIntegration.SILENT_EMOJI;
import static shrx.discordintegration.DiscordIntegration.WATCHER_EMOJI;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.megacrit.cardcrawl.screens.charSelect.CharacterSelectScreen;

public final class CharacterSelectScreenPatch {
    private static final DiscordIntegration discordIntegration = DiscordIntegration.getInstance();

    private CharacterSelectScreenPatch() {}

    @SpirePatch2(clz = CharacterSelectScreen.class, method = "open")
    public static class CharacterSelectScreenPostfixPatch {

        private CharacterSelectScreenPostfixPatch() {}

        public static void Postfix() {
            discordIntegration.uploadScreenshotWithReactions(IRONCLAD_EMOJI, SILENT_EMOJI, DEFECT_EMOJI, WATCHER_EMOJI);
        }
    }
}