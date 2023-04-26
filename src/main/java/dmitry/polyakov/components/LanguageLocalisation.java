package dmitry.polyakov.components;

import com.vdurmont.emoji.EmojiParser;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * @author Dmitry Polyakov
 * @created 06.02.2023 14:53
 */
public class LanguageLocalisation {
    public static ResourceBundle messages = ResourceBundle.getBundle("messages", new Locale("ru"));
    public static final String russianLang = EmojiParser.parseToUnicode("Русский:ru:");
    public static final String englishLang = EmojiParser.parseToUnicode("English:gb:");

}
