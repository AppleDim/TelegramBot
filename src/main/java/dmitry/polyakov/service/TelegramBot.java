package dmitry.polyakov.service;

import com.vdurmont.emoji.EmojiParser;
import dmitry.polyakov.components.MenuCommands;
import dmitry.polyakov.config.BotConfig;
import dmitry.polyakov.constants.BotStateEnum;
import dmitry.polyakov.model.User;
import dmitry.polyakov.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.helper.ValidationException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

/**
 * @author Dmitry Polyakov
 * @created 10.01.2023 23:47
 */
@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final BotConfig config;
    private UserRepository userRepository;
    public static ResourceBundle messages = LanguageLocalisation.defaultMessages;
    private static Map<String, String> regions;
    private static Map<String, String> settlements;
    private static String words;
    private static String region = "";
    private static String settlement = "";
    private BotStateEnum state = BotStateEnum.DEFAULT_STATE;
    int k = 0;
    int page = 1;

    @Autowired
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public TelegramBot(BotConfig config) {
        this.config = config;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            this.execute(new SetMyCommands(MenuCommands.addBotCommands(),
                    new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error occurred while attempting this command: ", e);
        }
        if (update.hasMessage() && update.getMessage().hasLocation() && state == BotStateEnum.SHOW_WEATHER_GETTER_WAY) {
            Location location = update.getMessage().getLocation();
            double longitude = location.getLongitude();
            double latitude = location.getLatitude();
            long chatId = update.getMessage().getChatId();
            getWeatherFromLocation(chatId, longitude, latitude);
            state = BotStateEnum.DEFAULT_STATE;
            log.info("User @" + update.getMessage().getChat().getUserName() + " sent his location");
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            if (messageText.equals("/start")) {
                registerUser(update.getMessage());
                startCommandReceived(chatId, update.getMessage().getChat().getUserName());
                sendMessage(chatId, messages.getString("click_button"));
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals("/help")) {
                sendMessage(chatId, messages.getString("help_text"));
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals("/register")) {
                confirmScheduledInfoReceipt(chatId);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals("/language")) {
                changeLanguageKeyboardDisplay(chatId);
                state = BotStateEnum.SHOW_LANGUAGES;
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals("/dictionary")
                    || messageText.equals(EmojiParser.parseToUnicode(messages
                    .getString("dictionary") + ":gb:" + ":abc:" + ":ru:"))) {
                state = BotStateEnum.ASK_PHRASE;
                sendMessage(chatId, messages.getString("enter_word"));
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals("/weather")
                    || messageText.equals(EmojiParser.parseToUnicode(messages
                    .getString("weather_button") + ":thunder_cloud_rain:"))) {
                displayWeatherMenu(chatId);
                state = BotStateEnum.SHOW_WEATHER_GETTER_WAY;
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals(LanguageLocalisation.englishLang)
                    || messageText.equals(LanguageLocalisation.russianLang)
                    || messageText.equals(LanguageLocalisation.germanLang)) {
                languageChange(update);
                state = BotStateEnum.DEFAULT_STATE;
            } else if (messageText.equals("/mydata")) {
                String info = myDataCommandReceived(chatId);
                sendMessage(chatId, info);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (state == BotStateEnum.SHOW_WEATHER_GETTER_WAY && messageText.equals("1. Отправить моё местоположение")) {
                state = BotStateEnum.ASK_WHEREABOUTS;
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (state == BotStateEnum.SHOW_WEATHER_GETTER_WAY && messageText.equals("2. Список регионов")) {
                state = BotStateEnum.SHOW_REGION_LIST;
                getRegion(chatId);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (state == BotStateEnum.ASK_PHRASE) {
                words = update.getMessage().getText();
                state = BotStateEnum.ASK_DICTIONARY_OPTION;
                displayDictionaryMenu(chatId);
            } else if (messageText.equals(EmojiParser.parseToUnicode("1. English meanings" + ":clipboard:"))
                    && state == BotStateEnum.ASK_DICTIONARY_OPTION) {
                displayMeaningsOfWord(chatId);
                words = null;
                state = BotStateEnum.DEFAULT_STATE;
            } else if (messageText.equals(EmojiParser.parseToUnicode("2. Russian-english sentences" + ":gb:" + ":ru:"))
                    && state == BotStateEnum.ASK_DICTIONARY_OPTION) {
                displayRusEngSentences(chatId);
                words = null;
                state = BotStateEnum.DEFAULT_STATE;
            } else if (messageText.equals(EmojiParser.parseToUnicode("3. English sentences" + ":abc:" + ":gb:"))
                    && state == BotStateEnum.ASK_DICTIONARY_OPTION) {
                displayExamplesWithSentences(chatId);
                words = null;
                state = BotStateEnum.DEFAULT_STATE;
            } else {
                if (state == BotStateEnum.DEFAULT_STATE) {
                    k = 0;
                    sendMessage(chatId, messages.getString("non-existing_command"));
                    log.info("User @" + update.getMessage().getChat().getUserName()
                            + " tried to execute non-existing command "
                            + update.getMessage().getText() + ". ");
                }
            }
        } else if (update.hasCallbackQuery()) {
            String callBackData = update.getCallbackQuery().getData();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            switch (callBackData) {
                case "YES_BUTTON" -> {
                    String yesPressed = messages.getString("yes_pressed");
                    executeEditMessage(messageId, chatId, yesPressed);
                }
                case "NO_BUTTON" -> {
                    String noPressed = messages.getString("no_pressed");
                    executeEditMessage(messageId, chatId, noPressed);
                }
                case "BACK_BUTTON" -> {
                    if (state == BotStateEnum.SHOW_REGION_LIST && k != 0) {
                        k = k - 10;
                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setMessageId(messageId);
                        deleteMessage.setChatId(String.valueOf(chatId));
                        tryToExecuteMessage(deleteMessage);
                        --page;
                        getRegion(chatId);
                    } else if (state == BotStateEnum.SHOW_SETTLEMENT_LIST && k != 0) {
                        k = k - 10;
                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setMessageId(messageId);
                        deleteMessage.setChatId(String.valueOf(chatId));
                        tryToExecuteMessage(deleteMessage);
                        --page;
                        getSettlement(chatId);
                    } else {
                        k = 0;
                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setMessageId(messageId);
                        deleteMessage.setChatId(String.valueOf(chatId));
                        tryToExecuteMessage(deleteMessage);
                        if (state == BotStateEnum.SHOW_REGION_LIST) getRegion(chatId);
                        else if (state == BotStateEnum.SHOW_SETTLEMENT_LIST) getSettlement(chatId);
                    }
                }
                case "CANCEL_BUTTON" -> {
                    state = BotStateEnum.DEFAULT_STATE;
                    executeEditMessage(messageId, chatId, messages.getString("click_button"));
                    sendMessage(chatId, "Main menu");
                }
                case "FORWARD_BUTTON" -> {
                    if (state == BotStateEnum.SHOW_REGION_LIST && regions.size() != 0) {
                        k = k + 10;
                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setMessageId(messageId);
                        deleteMessage.setChatId(String.valueOf(chatId));
                        tryToExecuteMessage(deleteMessage);
                        ++page;
                        getRegion(chatId);
                    } else if (state == BotStateEnum.SHOW_SETTLEMENT_LIST && settlements.size() != 0) {
                        k = k + 10;
                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setMessageId(messageId);
                        deleteMessage.setChatId(String.valueOf(chatId));
                        tryToExecuteMessage(deleteMessage);
                        ++page;
                        getSettlement(chatId);
                    } else {
                        DeleteMessage deleteMessage = new DeleteMessage();
                        deleteMessage.setMessageId(messageId);
                        deleteMessage.setChatId(String.valueOf(chatId));
                        tryToExecuteMessage(deleteMessage);
                        if (state == BotStateEnum.SHOW_REGION_LIST) {
                            page = 1;
                            k = 0;
                            getRegion(chatId);
                        } else if (state == BotStateEnum.SHOW_SETTLEMENT_LIST) {
                            page = 1;
                            k = 0;
                            getSettlement(chatId);
                        }
                    }
                }
            }
            if (state == BotStateEnum.SHOW_REGION_LIST) {
                for (Map.Entry<String, String> entry : regions.entrySet()) {
                    if (entry.getKey().equals(callBackData)) {
                        executeEditMessage(messageId, chatId, messages.getString("region_chosen") + " "
                                + callBackData);
                        region = callBackData;
                        state = BotStateEnum.SHOW_SETTLEMENT_LIST;
                        page = 1;
                        k = 0;
                        break;
                    }
                }
                getSettlement(chatId);
            }
            if (state == BotStateEnum.SHOW_SETTLEMENT_LIST) {
                for (Map.Entry<String, String> entry : settlements.entrySet()) {
                    if (entry.getKey().equals(callBackData)) {
                        settlement = callBackData;
                        executeEditMessage(messageId, chatId, messages.getString("settlement_chosen")
                                + callBackData);
                        state = BotStateEnum.SHOW_WEATHER_IN_SETTLEMENT;
                        page = 1;
                        k = 0;
                        break;
                    }
                }
            }
            if (state == BotStateEnum.SHOW_WEATHER_IN_SETTLEMENT) {
                getWeatherFromSettlement(chatId);
                state = BotStateEnum.DEFAULT_STATE;
            }
        }
    }

    private void getWeatherFromLocation(long chatId, double longitude, double latitude) {
        String sunRise = EmojiParser.parseToUnicode(":sunrise:");
      /*  String sunny = EmojiParser.parseToUnicode(":sunny:");
        String cloudy = EmojiParser.parseToUnicode(":cloud:");
        String snowy = EmojiParser.parseToUnicode(":cloud_with_snow:");
        String rainy = EmojiParser.parseToUnicode(":rain_cloud:");
        String partlySunny = EmojiParser.parseToUnicode(":partly_sunny:");
        String lightningWithRain = EmojiParser.parseToUnicode(":cloud_with_lightning_and_rain:");*/
        try {
            Document doc = Jsoup.connect(String.format("https://yandex.ru/pogoda/?lat=%f&lon=3%f&via=hnav",
                    longitude, latitude)).get();
            sendMessage(chatId, doc.getElementById("main_title").text());
            Elements elements = doc.body().getElementsByClass("a11y-hidden");
            StringBuilder sb = new StringBuilder();
            for (Element element : elements) {
                if (!element.text().contains("Закат")
                        || !element.text().contains("Восход")
                        || !element.text().contains("Погода на карте")) {
                    String str = element.text()
                            .replace("В ", EmojiParser.parseToUnicode(":thermometer:"))
                            .replace(" часа", ":00")
                            .replace(" часов", ":00")
                            .replace(" час", ":00");
                    sb.append(str).append("\n");
                }
            }
            sendMessage(chatId, sb.toString());
            sendMessage(chatId, doc.body().getElementsByClass("sun-card__day-duration").text());
        } catch (IOException e) {
            log.warn("Error occurred while attempting this command: ", e);
        }
    }

    private void displayRusEngSentences(long chatId) {
        try {
            String url = "https://context.reverso.net/перевод/английский-русский/" + words;
            Document doc = Jsoup.connect(url).get();
            Elements elements = doc.body().getElementsByClass("example");
            for (Element element : elements) {
                sendMessage(chatId, element.getElementsByClass("trg ltr").text()
                        + "\n" + element.getElementsByClass("src ltr").text());
            }
        } catch (IOException e) {
            sendMessage(chatId, "Word or phrase hasn't been found.");
            words = null;
            state = BotStateEnum.DEFAULT_STATE;
            log.warn("Error occurred while attempting this command: ", e);
        }
    }

    private void displayDictionaryMenu(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText("""
                Что хотите увидеть:
                1. Разные значения слова на английском (только для английских слов)\s
                2. Примеры предложений со словом на английском с переводом (слова могут быть и на русском, и на английском)\s
                3. Примеры предложений со словом на английском (только для английских слов)""");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        modifyKeyboardSettings(keyboardMarkup);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();

        KeyboardButton meaningsButton = new KeyboardButton();
        meaningsButton.setText(EmojiParser.parseToUnicode("1. English meanings" + ":clipboard:"));

        KeyboardButton rusEngSentencesButton = new KeyboardButton();
        rusEngSentencesButton.setText(EmojiParser.parseToUnicode("2. Russian-english sentences" + ":gb:" + ":ru:"));

        KeyboardButton engSentencesButton = new KeyboardButton();
        engSentencesButton.setText(EmojiParser.parseToUnicode("3. English sentences" + ":abc:" + ":gb:"));

        row1.add(meaningsButton);
        row2.add(rusEngSentencesButton);
        row3.add(engSentencesButton);

        keyboardRows.add(row1);
        keyboardRows.add(row2);
        keyboardRows.add(row3);

        keyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(keyboardMarkup);
        tryToExecuteMessage(sendMessage);
    }

    private void displayMeaningsOfWord(long chatId) {
        try {
            Document doc = Jsoup.connect("https://dictionary.cambridge.org/dictionary/english/" + words).get();
            Elements elements = doc.getElementsByClass("sense-body dsense_b");
            for (Element element : elements) {
                sendMessage(chatId, element.getElementsByClass("def ddef_d db")
                        .text().replace(":", "\n"));
            }
        } catch (IOException e) {
            sendMessage(chatId, "Word or phrase hasn't been found.");
            words = null;
            state = BotStateEnum.DEFAULT_STATE;
            log.warn("Error occurred while attempting this command: ", e);
        }
    }

    private void displayExamplesWithSentences(long chatId) {
        try {
            Document doc = Jsoup.connect("https://dictionary.cambridge.org/dictionary/english/" + words).get();
            Elements elements3 = doc.body().getElementsByClass("lbb lb-cm lpt-10");
            for (Element element : elements3) {
                sendMessage(chatId, element.getElementsByClass("deg").text());
            }
        } catch (IOException e) {
            sendMessage(chatId, "Word or phrase hasn't been found.");
            words = null;
            state = BotStateEnum.DEFAULT_STATE;
            log.warn("Error occurred while attempting this command: ", e);
        }
    }

    private void executeEditMessage(int messageId, long chatId, String text) {
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId(messageId);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn("Error occurred while attempting this command: ", e);
        }
    }

    private void confirmScheduledInfoReceipt(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText("Confirm your registration.");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText("Yes");
        yesButton.setCallbackData("YES_BUTTON");

        InlineKeyboardButton noButton = new InlineKeyboardButton();
        noButton.setText("No");
        noButton.setCallbackData("NO_BUTTON");

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInline.setKeyboard(rowsInLine);
        sendMessage.setReplyMarkup(markupInline);

        tryToExecuteMessage(sendMessage);
    }

    private String myDataCommandReceived(long chatId) {
        String msg = "";
        if (userRepository.findById(chatId).isPresent()) {
            User user = userRepository.findById(chatId).get();
            msg = " " + user.getUserName() + "\n"
                    + user.getRegisteredTime() + "\n"
                    + user.getFirstName() + "\n"
                    + user.getChatId();
        }
        return msg;
    }

    private void displayWeatherMenu(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText("""
                Выберите способ получения погоды:
                1. Получить погоду по вашему местоположению;\s
                2. Получить погоду по выбранному населённому пункту\s
                Для этого нажмите на кнопку""");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        modifyKeyboardSettings(keyboardMarkup);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();

        KeyboardButton locationWeatherButton = new KeyboardButton();
        locationWeatherButton.setText("1. Отправить моё местоположение");
        locationWeatherButton.setRequestLocation(true);

        KeyboardButton getRegionsButton = new KeyboardButton();
        getRegionsButton.setText("2. Список регионов");

        row1.add(locationWeatherButton);
        row2.add(getRegionsButton);

        keyboardRows.add(row1);
        keyboardRows.add(row2);

        keyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(keyboardMarkup);
        tryToExecuteMessage(sendMessage);
    }

    private String getUrl(Map<String, String> elements, String callbackData) {
        String elementUrl = null;
        for (Map.Entry<String, String> entry : elements.entrySet()) {
            if (entry.getKey().equals(callbackData))
                elementUrl = entry.getValue();
        }
        return elementUrl;
    }

    private Document extractCallbackDataUrlFromElements(Map<String, String> elements, String callbackData) {
        Document doc = null;
        try {
            doc = Jsoup.connect(getUrl(elements, callbackData)).get();
        } catch (IOException | ValidationException | NullPointerException e) {
            log.warn("Error occurred while attempting this command: ", e);
            state = BotStateEnum.DEFAULT_STATE;
        }

        return doc;
    }

    private Document connectToUrl(String url) {
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException | ValidationException | NullPointerException e) {
            log.warn("Error occurred while attempting this command: ", e);
            state = BotStateEnum.DEFAULT_STATE;
        }

        return doc;
    }

    private void getRegion(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(messages.getString("region_list") + page + ": ");

        Document doc = connectToUrl("https://world-weather.ru/pogoda/russia");
        Elements links = doc.select("a[href]");

        if (state == BotStateEnum.SHOW_REGION_LIST) {
            regions = new TreeMap<>();
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            int j = 0;
            for (int i = k; j < 10; i++) {
                try {
                    if (links.get(i).attr("href").contains("/pogoda")
                            && !links.get(i).text().equals("Весь мир")) {
                        j++;
                        regions.put(links.get(i).text(), links.get(i).attr("href"));
                    }
                } catch (IndexOutOfBoundsException e) {
                    break;
                }
            }
            int i = 0;
            for (Map.Entry<String, String> entry : regions.entrySet()) {
                inlineKeyboardCreate(rowsInline, ++i, entry.getKey());
            }
            putGeneralButtons(rowsInline);
            markupInline.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(markupInline);
            tryToExecuteMessage(sendMessage);
        }
    }

    private void getSettlement(Long chatId) {
        if (state == BotStateEnum.SHOW_SETTLEMENT_LIST) {
            settlements = new TreeMap<>();
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(messages.getString("settlement_list") + page + ": ");
            Document doc = extractCallbackDataUrlFromElements(regions, region);

            Elements links2 = doc.select("a[href]");
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            int j = 0;
            for (int i = k; j < 10; i++) {
                try {
                    if (links2.get(i).attr("href").contains("/pogoda")
                            && !links2.get(i).text().equals("Весь мир")
                            && !links2.get(i).text().equals("Россия")) {
                        j++;
                        settlements.put(links2.get(i).text(), "http:" + links2.get(i).attr("href"));
                    }
                } catch (IndexOutOfBoundsException e) {
                    break;
                }
            }
            int i = 0;
            for (Map.Entry<String, String> entry : settlements.entrySet()) {
                inlineKeyboardCreate(rowsInline, ++i, entry.getKey());
            }
            putGeneralButtons(rowsInline);
            markupInline.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(markupInline);
            tryToExecuteMessage(sendMessage);
        }
    }

    private void inlineKeyboardCreate(List<List<InlineKeyboardButton>> rowsInline, int i, String linkText) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText(i + ". " + linkText);
        inlineKeyboardButton.setCallbackData(linkText);
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        rowInLine.add(inlineKeyboardButton);
        rowsInline.add(rowInLine);
    }

    private void putGeneralButtons(List<List<InlineKeyboardButton>> rowsInline) {
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(EmojiParser.parseToUnicode(":arrow_left:"));
        backButton.setCallbackData("BACK_BUTTON");

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText(EmojiParser.parseToUnicode(":x:"));
        cancelButton.setCallbackData("CANCEL_BUTTON");

        InlineKeyboardButton forwardButton = new InlineKeyboardButton();
        forwardButton.setText(EmojiParser.parseToUnicode(":arrow_right:"));
        forwardButton.setCallbackData("FORWARD_BUTTON");

        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        rowInLine.add(backButton);
        rowInLine.add(cancelButton);
        rowInLine.add(forwardButton);

        rowsInline.add(rowInLine);
    }

    private void getWeatherFromSettlement(Long chatId) {
        Document doc = extractCallbackDataUrlFromElements(settlements, settlement);
        Elements elements = doc.getElementsByClass("weather-now");

        String str1 = "";
        String str2 = "";
        for (Element element : elements) {
            str2 = String.valueOf(element.getElementsByClass("tooltip"));
            str1 = String.valueOf(element.getElementsByClass("weather-now-info"));
        }

        StringBuilder sb = new StringBuilder();

        String[] arr = str1.split("\n");
        sb
                .append(arr[7].split("\"")[3])
                .append(arr[7].split("\"")[4]
                        .replace("></em><b>", ": ")
                        .replace("</b></p>", ""))
                .append("\nТемпература сейчас:")
                .append(arr[9].replace("<span>", "")
                        .replace("</span>", "\n")
                        .replaceFirst(" ", ""));

        String[] arr2 = str2.split("\n");
        sb
                .append(arr2[1]
                        .split("\"")[1])
                .append("\n")
                .append("Ветер: ")
                .append(arr2[3]
                        .split("\"")[1])
                .append(", ")
                .append(arr2[4]
                        .split("\"")[1]);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage(chatId, sb.toString());
    }

    private void changeLanguageKeyboardDisplay(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(messages.getString("choose_language"));

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        modifyKeyboardSettings(keyboardMarkup);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();

        KeyboardButton engButton = new KeyboardButton();
        engButton.setText(LanguageLocalisation.englishLang);

        KeyboardButton ruButton = new KeyboardButton();
        ruButton.setText(LanguageLocalisation.russianLang);

        KeyboardButton deButton = new KeyboardButton();
        deButton.setText(LanguageLocalisation.germanLang);

        row.add(engButton);
        row.add(ruButton);
        row.add(deButton);

        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(keyboardMarkup);

        tryToExecuteMessage(sendMessage);
    }

    private void modifyKeyboardSettings(ReplyKeyboardMarkup keyboardMarkup) {
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);
    }

    private void languageChange(Update update) {
        long chatId = update.getMessage().getChatId();

        if (state == BotStateEnum.SHOW_LANGUAGES) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            String messageText = update.getMessage().getText();

            if (messageText.equals(LanguageLocalisation.englishLang)) {
                messages = ResourceBundle.getBundle("messages", Locale.of("en"));

            } else if (messageText.equals(LanguageLocalisation.russianLang)) {
                messages = ResourceBundle.getBundle("messages", Locale.of("ru"));

            } else if (messageText.equals(LanguageLocalisation.germanLang)) {
                messages = ResourceBundle.getBundle("messages", Locale.of("de"));
            }

            sendMessage(chatId, messages.getString("swap_language"));

            state = BotStateEnum.DEFAULT_STATE;
            log.info("@" + update.getMessage().getChat().getUserName() + " changed language to "
                    + messages.getString("language_name"));

        } else
            sendMessage(chatId, messages.getString("non-existing_command"));
    }

    private void registerUser(Message msg) {
        if (userRepository.findById(msg.getChatId()).isEmpty()) {
            Long chatId = msg.getChatId();
            Chat chat = msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setUserName(chat.getUserName());
            user.setRegisteredTime(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("User saved: " + user);
        }
    }

    private void startCommandReceived(long chatId, String firstName) {
        String greeting = messages.getString("greeting");
        String answer = EmojiParser.parseToUnicode(greeting + firstName + " :wave:");
        log.info("@" + firstName + " launched the bot.");
        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        modifyKeyboardSettings(keyboardMarkup);
        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();

        KeyboardButton weatherButton =
                new KeyboardButton(EmojiParser.parseToUnicode(messages
                        .getString("weather_button") + ":thunder_cloud_rain:"));
        row.add(weatherButton);

        KeyboardButton sentencesButton =
                new KeyboardButton(EmojiParser.parseToUnicode(messages
                        .getString("dictionary") + ":gb:" + ":abc:" + ":ru:"));
        row.add(sentencesButton);

        KeyboardButton askLocationButton = new KeyboardButton("Отослать свою геолокацию");
        askLocationButton.setRequestLocation(true);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(askLocationButton);

        keyboardRows.add(row);
        keyboardRows.add(row2);

        keyboardMarkup.setKeyboard(keyboardRows);

        sendMessage.setReplyMarkup(keyboardMarkup);

        tryToExecuteMessage(sendMessage);
    }

    private void executeMessage(long chatId, String messageText) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(messageText);
        sendMessage(chatId, sendMessage.getText());
        tryToExecuteMessage(sendMessage);
    }

    private void tryToExecuteMessage(BotApiMethod<?> message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn("Error occurred while attempting this command: ", e);
        }
    }
}