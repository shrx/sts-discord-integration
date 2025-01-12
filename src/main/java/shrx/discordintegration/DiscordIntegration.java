package shrx.discordintegration;

import javax.imageio.ImageIO;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.actions.common.EndTurnAction;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.screens.charSelect.CharacterOption;
import com.megacrit.cardcrawl.screens.mainMenu.MainMenuScreen;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.yaml.snakeyaml.Yaml;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.win32.StdCallLibrary;

@SpireInitializer
public class DiscordIntegration extends ListenerAdapter implements PostUpdateSubscriber {
    private static volatile DiscordIntegration botInstance;

    private static final Logger logger = LogManager.getLogger(DiscordIntegration.class.getName());

    private static final File SCREENSHOT_FILE = new File("screenshot.png");
    private static final String CONFIG_PATH = "mods/discord-integration-config.yaml";

    public static final String STOP_EMOJI = "U+1f6d1";
    public static final String IRONCLAD_EMOJI = "U+1f7e5";
    public static final String SILENT_EMOJI = "U+1f7e9";
    public static final String DEFECT_EMOJI = "U+1f7e6";
    public static final String WATCHER_EMOJI = "U+1f7ea";

    private final String guildId;
    private final String channelId;

    private TextChannel channel;
    private String monitoredMessageId;

    private AbstractDungeon.CurrentScreen oldScreen = null;
    private AbstractRoom.RoomPhase oldPhase = null;
    private MainMenuScreen.CurScreen oldMenuScreen = null;

    private static final ScheduledExecutorService screenshotter = Executors.newSingleThreadScheduledExecutor();

    public static void initialize() throws InterruptedException, IOException {
        Map<String, Object> config = loadConfig();

        String token = ((String) config.get("token"));
        String guildId = ((String) config.get("guildId"));
        String channelId = ((String) config.get("channelId"));

        EnumSet<GatewayIntent> intents = EnumSet.of(
                // Enables MessageReceivedEvent for guild (also known as servers)
                GatewayIntent.GUILD_MESSAGES,
                // Enables access to message.getContentRaw()
                GatewayIntent.MESSAGE_CONTENT,
                // Enables MessageReactionAddEvent for guild
                GatewayIntent.GUILD_MESSAGE_REACTIONS);

        JDABuilder builder = JDABuilder.createLight(token, intents);
        botInstance = new DiscordIntegration(guildId, channelId);
        builder.addEventListeners(botInstance);
        JDA jda = builder.build();
        jda.awaitReady();

        logger.info("Discord bot initialized successfully!");
    }

    private DiscordIntegration(String guildId, String channelId) {
        this.guildId = guildId;
        this.channelId = channelId;
        BaseMod.subscribe(this);
    }

    public static DiscordIntegration getInstance() {
        return botInstance;
    }

    // from Basemod PostUpdateSubscriber

    @Override
    public synchronized void receivePostUpdate() {
        final AbstractDungeon.CurrentScreen screen = AbstractDungeon.screen;
        final AbstractRoom.RoomPhase phase;
        final MainMenuScreen.CurScreen menuScreen;
        if (CardCrawlGame.mainMenuScreen == null) {
            menuScreen = null;
        } else {
            menuScreen = CardCrawlGame.mainMenuScreen.screen;
        }
        if (AbstractDungeon.currMapNode != null && AbstractDungeon.getCurrRoom() != null) {
            phase = AbstractDungeon.getCurrRoom().phase;
        } else {
            phase = null;
        }

        boolean somethingChanged = false;

        if (screen != oldScreen) {
            oldScreen = screen;
            somethingChanged = true;
        }

        if (phase != oldPhase) {
            oldPhase = phase;
            somethingChanged = true;
        }

        if (menuScreen != oldMenuScreen) {
            oldMenuScreen = menuScreen;
            somethingChanged = true;
        }

        if (somethingChanged) {
            logger.info("Current phase={} screen={} menuScreen={}", phase, screen, menuScreen);
        }
    }

    // from JDA ListenerAdapter

    @Override
    public void onReady(ReadyEvent event) {
        logger.info("Discord connection ready");
        channel = event.getJDA().getGuildById(guildId).getTextChannelById(channelId);
        channel.sendMessage("StS discord integration bot started").queue();
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Check if the reaction is in the monitored guild and channel
        if (event.getGuild().getId().equals(guildId) && event.getChannel().getId().equals(channelId)
                && event.getMessageId().equals(monitoredMessageId) && !event.getUser().isBot()) {

            if (oldMenuScreen == MainMenuScreen.CurScreen.CHAR_SELECT) {
                final ArrayList<CharacterOption> options = CardCrawlGame.mainMenuScreen.charSelectScreen.options;
                CharacterOption character = null;
                switch (event.getReaction().getEmoji().asUnicode().getAsCodepoints()) {
                    case IRONCLAD_EMOJI:
                        character = options.get(0);
                        break;
                    case SILENT_EMOJI:
                        character = options.get(1);
                        break;
                    case DEFECT_EMOJI:
                        character = options.get(2);
                        break;
                    case WATCHER_EMOJI:
                        character = options.get(3);
                        break;
                    default:
                        break;
                }
                character.hb.clicked = true;
                CardCrawlGame.mainMenuScreen.charSelectScreen.confirmButton.hb.clicked = true;
            }

            if (event.getReaction().getEmoji().getAsReactionCode().equals(STOP_EMOJI)) {

                endTurnInGame();
                logger.info("Turn ended in the game");
            }
        }
    }

    // Public integration methods

    public void uploadScreenshotWithReactions(final String... reactionEmojis) {
        screenshotter.schedule(() -> {
            try {
                takeGameScreenshot();
            } catch (AWTException | IOException e) {
                throw new RuntimeException(e);
            }

            if (channel != null) {
                channel.sendFiles(FileUpload.fromData(SCREENSHOT_FILE)).queue((message) -> {
                    monitoredMessageId = message.getId();
                    Arrays.stream(reactionEmojis).sequential()
                            .forEach(e -> message.addReaction(Emoji.fromUnicode(e)).queue());
                });
            }

        }, 100, TimeUnit.MILLISECONDS);
    }

    // Private utility methods

    private void endTurnInGame() {
        if (AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().isBattleOver) {
            logger.warn("Cannot end turn: Battle is already over.");
            return;
        }

        if (AbstractDungeon.actionManager != null) {
            AbstractDungeon.actionManager.clearPostCombatActions();
            AbstractDungeon.actionManager.addToBottom(new EndTurnAction());

            logger.debug("Turn ended in the game!");
        } else {
            logger.warn("Failed to end turn: Action manager is null.");
        }
    }

    private void takeGameScreenshot() throws AWTException, IOException {
        int hWnd = Win32API.User32.instance.FindWindowA(null, "Modded Slay the Spire");
        Win32API.WindowInfo w = Win32API.getWindowInfo(hWnd);
        Win32API.User32.instance.SetForegroundWindow(w.hwnd);
        BufferedImage createScreenCapture = new Robot().createScreenCapture(
                new Rectangle(w.rect.left, w.rect.top, w.rect.right - w.rect.left, w.rect.bottom - w.rect.top));
        ImageIO.write(createScreenCapture, "png", SCREENSHOT_FILE);
    }

    private static Map<String, Object> loadConfig() throws IOException {
        Yaml yaml = new Yaml();
        File configFile = new File(CONFIG_PATH);

        if (!configFile.exists()) {
            throw new IllegalStateException(
                    "Configuration file ' " + CONFIG_PATH + " ' not found in " + configFile.getAbsolutePath());
        }

        try (InputStream inputStream = new FileInputStream(configFile)) {
            return yaml.load(inputStream);
        }
    }

}
