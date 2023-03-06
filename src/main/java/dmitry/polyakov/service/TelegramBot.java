package dmitry.polyakov.service;

import com.vdurmont.emoji.EmojiParser;
import dmitry.polyakov.components.Menu;
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
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
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

import static dmitry.polyakov.constants.BotMenuCommand.*;
import static dmitry.polyakov.constants.BotStateEnum.*;

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
    private static final Map<Long, BotStateEnum> userMap = new HashMap<>();
    private static Map<String, String> settlements;
    private static String words;
    private static String region = "";
    private static String settlement = "";
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
            this.execute(new SetMyCommands(Menu.addBotCommands(),
                    new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error occurred while attempting this command: ", e);
        }
        if (update.hasMessage() && update.getMessage().hasLocation()) {
            long chatId = update.getMessage().getChatId();
            Location location = update.getMessage().getLocation();
            double longitude = location.getLongitude();
            double latitude = location.getLatitude();
            if (userRepository.findById(chatId).isPresent()) {
                User user = userRepository.findById(chatId).get();
                user.setLocationCoordinates(String.format("%s, %s", longitude, latitude));
                userRepository.save(user);
                log.info("User's location updated: " + user);
            }
            getWeatherFromLocation(chatId, longitude, latitude);
            userMap.replace(chatId, DEFAULT_STATE);
            log.info("User @" + update.getMessage().getChat().getUserName() + " sent his location");

        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            try {
                executeTyping(chatId);
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.warn("Error occurred while attempting this command: ", e);
            }
            if (messageText.equals(START)) {
                registerUser(update.getMessage());
                startCommandReceived(chatId, update.getMessage().getChat().getUserName());
                sendMessage(chatId, messages.getString("click_button"));
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals(HELP)) {
                sendMessage(chatId, messages.getString("help_text"));
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals(REGISTER)) {
                confirmScheduledInfoReceipt(chatId);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals(LANGUAGE)) {
                changeLanguageKeyboardDisplay(chatId);
                userMap.replace(chatId, SHOW_LANGUAGES);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals(DICTIONARY)
                    || messageText.equals(EmojiParser.parseToUnicode(messages
                    .getString("dictionary") + ":gb:" + ":abc:" + ":ru:"))) {
                userMap.replace(chatId, ASK_PHRASE);
                sendMessage(chatId, messages.getString("enter_word"));
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals(WEATHER)
                    || messageText.equals(EmojiParser.parseToUnicode(messages
                    .getString("weather_button") + ":thunder_cloud_rain:"))) {
                k = 0;
                page = 1;
                displayWeatherMenu(chatId);
                userMap.replace(chatId, SHOW_WEATHER_GETTER_WAY);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals(LanguageLocalisation.englishLang)
                    || messageText.equals(LanguageLocalisation.russianLang)) {
                languageChange(update);
                userMap.replace(chatId, DEFAULT_STATE);
            } else if (messageText.equals(MY_DATA)) {
                String info = myDataCommandReceived(chatId);
                sendMessage(chatId, info);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (isUserMatchedByIdAndBotState(chatId, SHOW_WEATHER_GETTER_WAY)
                    && messageText.equals("1. Погода по местоположению")) {
                if (userRepository.findById(chatId).isPresent()) {
                    User user = userRepository.findById(chatId).get();
                    String coordinates = user.getLocationCoordinates();
                    String[] cord = coordinates.split(", ");
                    double longitude = Double.parseDouble(cord[0]);
                    double latitude = Double.parseDouble(cord[1]);
                    getWeatherFromLocation(chatId, longitude, latitude);
                }
                userMap.replace(chatId, DEFAULT_STATE);
                log.info("User @" + update.getMessage().getChat().getUserName() + " sent his location");
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (isUserMatchedByIdAndBotState(chatId, SHOW_WEATHER_GETTER_WAY)
                    && messageText.equals("2. Список регионов")) {
                userMap.replace(chatId, SHOW_REGION_LIST);
                getRegion(chatId);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (isUserMatchedByIdAndBotState(chatId, ASK_PHRASE)) {
                words = update.getMessage().getText();
                userMap.replace(chatId, ASK_DICTIONARY_OPTION);
                displayDictionaryMenu(chatId);
            } else if (messageText.equals(EmojiParser.parseToUnicode("2. English meanings" + ":clipboard:" + ":gb:"))
                    && isUserMatchedByIdAndBotState(chatId, ASK_DICTIONARY_OPTION)) {
                displayMeaningsOfWord(chatId);
                words = null;
                userMap.replace(chatId, DEFAULT_STATE);
            } else if (messageText.equals(EmojiParser.parseToUnicode("3. Russian-english sentences" + ":gb:" + ":ru:"))
                    && isUserMatchedByIdAndBotState(chatId, ASK_DICTIONARY_OPTION)) {
                displayRusEngSentences(chatId);
                words = null;
                userMap.replace(chatId, DEFAULT_STATE);
            } else if (messageText.equals(EmojiParser.parseToUnicode("4. English sentences" + ":abc:" + ":gb:"))
                    && isUserMatchedByIdAndBotState(chatId, ASK_DICTIONARY_OPTION)) {
                displayExamplesWithSentences(chatId);
                words = null;
                userMap.replace(chatId, DEFAULT_STATE);
            } else {
                if (isUserMatchedByIdAndBotState(chatId, DEFAULT_STATE)) {
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
            try {
                executeTyping(chatId);
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.warn("Error occurred while attempting this command: ", e);
            }
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
                    if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST) && k != 0) {
                        k = k - 10;
                        deleteMessage(messageId, chatId);
                        --page;
                        getRegion(chatId);
                    } else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST) && k != 0) {
                        k = k - 10;
                        deleteMessage(messageId, chatId);
                        --page;
                        getSettlement(chatId);
                    } else {
                        k = 0;
                        deleteMessage(messageId, chatId);
                        if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) getRegion(chatId);
                        else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)) getSettlement(chatId);
                    }
                }
                case "CANCEL_BUTTON" -> {
                    userMap.replace(chatId, DEFAULT_STATE);
                    executeEditMessage(messageId, chatId, messages.getString("click_button"));
                    sendMessage(chatId, "Main menu");
                }
                case "FORWARD_BUTTON" -> {
                    if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)
                            && page < countLines(chatId) / 10 + 1) {
                        k = k + 10;
                        deleteMessage(messageId, chatId);
                        ++page;
                        getRegion(chatId);
                    } else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)
                            && page < countLines(chatId) / 10 + 1) {
                        k = k + 10;
                        deleteMessage(messageId, chatId);
                        ++page;
                        getSettlement(chatId);
                    } else {
                        deleteMessage(messageId, chatId);
                        if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) getRegion(chatId);
                        else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)) getSettlement(chatId);
                    }
                }
            }
            if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) {
                for (Map.Entry<String, String> entry : regions.entrySet()) {
                    if (entry.getKey().equals(callBackData)) {
                        executeEditMessage(messageId, chatId, messages.getString("region_chosen") + " "
                                + callBackData);
                        region = callBackData;
                        userMap.replace(chatId, SHOW_SETTLEMENT_LIST);
                        page = 1;
                        k = 0;
                        break;
                    }
                }
                getSettlement(chatId);
            }
            if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)) {
                for (Map.Entry<String, String> entry : settlements.entrySet()) {
                    if (entry.getKey().equals(callBackData)) {
                        settlement = callBackData;
                        executeEditMessage(messageId, chatId, messages.getString("settlement_chosen")
                                + callBackData);
                        userMap.replace(chatId, SHOW_WEATHER_IN_SETTLEMENT);
                        page = 1;
                        k = 0;
                        break;
                    }
                }
            }
            if (isUserMatchedByIdAndBotState(chatId, SHOW_WEATHER_IN_SETTLEMENT)) {
                getWeatherFromSettlement(chatId);
                userMap.replace(chatId, DEFAULT_STATE);
            }
        }
    }

    private void deleteMessage(int messageId, long chatId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setMessageId(messageId);
        deleteMessage.setChatId(String.valueOf(chatId));
        tryToExecuteMessage(deleteMessage);
    }

    private void displayDictionaryMenu(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText("""
                Что хотите увидеть:
                1. Значения слова на русском\s
                1. Объяснение значения слова (только для английских слов)\s
                2. Примеры предложений со словом на английском с переводом\s
                3. Примеры предложений со словом на английском (только для английских слов)""");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        modifyKeyboardSettings(keyboardMarkup);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();
        KeyboardRow row3 = new KeyboardRow();
        KeyboardRow row4 = new KeyboardRow();

        KeyboardButton meaningsButton = new KeyboardButton();
        meaningsButton.setText(EmojiParser.parseToUnicode("1. Russian meanings" + ":clipboard:" + ":ru:"));

        KeyboardButton engExplanationsButton = new KeyboardButton();
        engExplanationsButton.setText(EmojiParser.parseToUnicode("2. English meanings" + ":clipboard:" + ":gb:"));

        KeyboardButton rusEngSentencesButton = new KeyboardButton();
        rusEngSentencesButton.setText(EmojiParser.parseToUnicode("3. Russian-english sentences" + ":gb:" + ":ru:"));

        KeyboardButton engSentencesButton = new KeyboardButton();
        engSentencesButton.setText(EmojiParser.parseToUnicode("4. English sentences" + ":abc:" + ":gb:"));

        row1.add(meaningsButton);
        row2.add(engExplanationsButton);
        row3.add(rusEngSentencesButton);
        row4.add(engSentencesButton);

        keyboardRows.add(row1);
        keyboardRows.add(row2);
        keyboardRows.add(row3);
        keyboardRows.add(row4);

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
            userMap.replace(chatId, DEFAULT_STATE);
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
            userMap.replace(chatId, DEFAULT_STATE);
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
            userMap.replace(chatId, DEFAULT_STATE);
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
        KeyboardRow row3 = new KeyboardRow();

        KeyboardButton locationWeatherButton = new KeyboardButton();
        locationWeatherButton.setText("1. Погода по местоположению");
        if (userRepository.findById(chatId).isPresent() && (userRepository.findById(chatId).get().getLocationCoordinates() == null)) {
            locationWeatherButton.setRequestLocation(true);
        }

        KeyboardButton getRegionsButton = new KeyboardButton();
        getRegionsButton.setText("2. Список регионов");

        KeyboardButton newLocationButton = new KeyboardButton();
        newLocationButton.setText("3. Отправить новое местоположение");
        newLocationButton.setRequestLocation(true);

        row1.add(locationWeatherButton);
        row2.add(getRegionsButton);
        row3.add(newLocationButton);

        keyboardRows.add(row1);
        keyboardRows.add(row2);
        keyboardRows.add(row3);

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

    private Document extractCallbackDataUrlFromElements(Map<String, String> elements, String callbackData, long chatId) {
        Document doc = null;
        try {
            doc = Jsoup.connect(getUrl(elements, callbackData)).get();
        } catch (IOException | ValidationException | NullPointerException e) {
            log.warn("Error occurred while attempting this command: ", e);
            userMap.replace(chatId, DEFAULT_STATE);
        }

        return doc;
    }

    private Document connectToUrl(String url, long chatId) {
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException | ValidationException | NullPointerException e) {
            log.warn("Error occurred while attempting this command: ", e);
            userMap.replace(chatId, DEFAULT_STATE);
        }

        return doc;
    }

    private void getWeatherFromLocation(long chatId, double longitude, double latitude) {
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

    private int countLines(long chatId) {
        int numOfLines = 0;
        if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) {
            Document doc = connectToUrl("https://world-weather.ru/pogoda/russia", chatId);
            Elements links = doc.select("a[href]");
            for (Element element : links) {
                if (element.attr("href").contains("/pogoda")
                        && !element.text().equals("Весь мир")) {
                    numOfLines++;
                }
            }
            return numOfLines;
        } else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)) {
            Document doc = extractCallbackDataUrlFromElements(regions, region, chatId);
            Elements links = doc.select("a[href]");
            for (Element element : links) {
                if (element.attr("href").contains("/pogoda")
                        && !element.text().equals("Весь мир")
                        && !element.text().equals("Россия")) {
                    numOfLines++;
                }
            }
            return numOfLines;
        }
        return 0;
    }

    private void getRegion(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(messages.getString("region_list") + page + "/" + (countLines(chatId) / 10 + 1) + ": ");

        Document doc = connectToUrl("https://world-weather.ru/pogoda/russia", chatId);
        Elements links = doc.select("a[href]");

        if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) {
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
            for (Map.Entry<String, String> regionEntry : regions.entrySet()) {
                inlineKeyboardCreate(rowsInline, ++i, regionEntry.getKey());
            }
            putGeneralButtons(rowsInline);
            markupInline.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(markupInline);
            tryToExecuteMessage(sendMessage);
        }
    }

    private void getSettlement(Long chatId) {
        if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)) {
            settlements = new TreeMap<>();
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(messages.getString("settlement_list") + page + "/" + (countLines(chatId) / 10 + 1) + ": ");
            Document doc = extractCallbackDataUrlFromElements(regions, region, chatId);

            Elements links = doc.select("a[href]");
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

            int j = 0;
            for (int i = k; j < 10; i++) {
                try {
                    if (links.get(i).attr("href").contains("/pogoda")
                            && !links.get(i).text().equals("Весь мир")
                            && !links.get(i).text().equals("Россия")) {
                        j++;
                        settlements.put(links.get(i).text(), "http:" + links.get(i).attr("href"));
                    }
                } catch (IndexOutOfBoundsException e) {
                    break;
                }
            }
            int i = 0;
            for (Map.Entry<String, String> settlementEntry : settlements.entrySet()) {
                inlineKeyboardCreate(rowsInline, ++i, settlementEntry.getKey());
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

    private void getWeatherFromSettlement(long chatId) {
        Document doc = extractCallbackDataUrlFromElements(settlements, settlement, chatId);
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

        row.add(engButton);
        row.add(ruButton);

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
        if (isUserMatchedByIdAndBotState(chatId, SHOW_LANGUAGES)) {
            String messageText = update.getMessage().getText();

            if (messageText.equals(LanguageLocalisation.englishLang)) {
                messages = ResourceBundle.getBundle("messages", Locale.of("en"));

            } else if (messageText.equals(LanguageLocalisation.russianLang)) {
                messages = ResourceBundle.getBundle("messages", Locale.of("ru"));
            }

            sendMessage(update.getUpdateId(), messages.getString("swap_language"));

            userMap.replace(chatId, DEFAULT_STATE);

            log.info("@" + update.getMessage().getChat().getUserName() + " changed language to "
                    + messages.getString("language_name"));
        } else sendMessage(update.getUpdateId(), messages.
                getString("non-existing_command"));
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
        userMap.putIfAbsent(chatId, DEFAULT_STATE);
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

    private void executeTyping(long chatId) {
        SendChatAction sendChatAction = new SendChatAction();
        sendChatAction.setAction(ActionType.TYPING);
        sendChatAction.setChatId(String.valueOf(chatId));
        tryToExecuteMessage(sendChatAction);
    }

    private void tryToExecuteMessage(BotApiMethod<?> message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn("Error occurred while attempting this command: ", e);
        }
    }

    private boolean isUserMatchedByIdAndBotState(long chatId, BotStateEnum botState) {
        for (Map.Entry<Long, BotStateEnum> entry : userMap.entrySet()) {
            if (entry.getKey().equals(chatId) && entry.getValue().equals(botState)) {
                return true;
            }
        }
        return false;
    }
}