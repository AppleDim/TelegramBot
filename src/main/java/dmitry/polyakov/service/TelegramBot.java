package dmitry.polyakov.service;

import com.vdurmont.emoji.EmojiParser;
import dmitry.polyakov.components.MenuCommands;
import dmitry.polyakov.config.BotConfig;
import dmitry.polyakov.constants.BotStateEnum;
import dmitry.polyakov.model.User;
import dmitry.polyakov.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.helper.ValidationException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
    private static Map<String, String> regions = new TreeMap<>();
    private static Map<String, String> settlements = new TreeMap<>();
    private static String words;
    private static String region = "";
    private static String settlement = "";
    private BotStateEnum state = BotStateEnum.DEFAULT_STATE;

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
            sendMessage(chatId, latitude + ", " + longitude);
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
            } else if (messageText.equals("/sentences")
                    || messageText.equals(EmojiParser.parseToUnicode(messages.getString("sentences") + ":abc:"))) {
                state = BotStateEnum.ASK_PHRASE;
                sendMessage(chatId, messages.getString("enter_word"));
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (messageText.equals("/weather")
                    || messageText.equals(EmojiParser.parseToUnicode(messages.getString("weather_button") + ":thunder_cloud_rain:"))) {
                chooseWayToGetWeather(chatId);
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
            } else if (state == BotStateEnum.SHOW_WEATHER_GETTER_WAY && messageText.equals("1")) {
                state = BotStateEnum.ASK_WHEREABOUTS;
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (state == BotStateEnum.SHOW_WEATHER_GETTER_WAY && messageText.equals("2")) {
                state = BotStateEnum.SHOW_REGION_LIST;
                getRegion(chatId);
                log.info(update.getMessage().getText() + " command executed by @"
                        + update.getMessage().getChat().getUserName());
            } else if (state == BotStateEnum.ASK_PHRASE) {
                words = update.getMessage().getText();
                displaySentences(chatId);
                state = BotStateEnum.DEFAULT_STATE;
                words = null;
            } else {
                if (state == BotStateEnum.DEFAULT_STATE) {
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
            }
            if (state == BotStateEnum.SHOW_REGION_LIST) {
                for (Map.Entry<String, String> entry : regions.entrySet()) {
                    if (entry.getKey().equals(callBackData)) {
                        executeEditMessage(messageId, chatId, messages.getString("region_chosen") + " " + callBackData);
                        region = callBackData;
                        state = BotStateEnum.SHOW_SETTLEMENT_LIST;
                        break;
                    }
                }
                getSettlement(chatId);
            }
            if (state == BotStateEnum.SHOW_SETTLEMENT_LIST) {
                for (Map.Entry<String, String> entry : settlements.entrySet()) {
                    if (entry.getKey().equals(callBackData)) {
                        settlement = callBackData;
                        executeEditMessage(messageId, chatId, messages.getString("settlement_chosen") + callBackData);
                        state = BotStateEnum.SHOW_WEATHER_IN_SETTLEMENT;
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

    private void getLocation(Update update) {
        if (update.hasMessage() && update.getMessage().hasLocation()) {
            Location location = update.getMessage().getLocation();
            double longitude = location.getLongitude();
            double latitude = location.getLatitude();
            long chatId = update.getMessage().getChatId();
            sendMessage(chatId, latitude + ", " + longitude);
            log.info("User @" + update.getMessage().getChat().getUserName() + " sent his location");
        }
    }

    private void displaySentences(long chatId) {
        Document doc = null;
        if (state == BotStateEnum.ASK_PHRASE) {
            try {
                String url = "https://context.reverso.net/перевод/английский-русский/" + words;
                doc = Jsoup.connect(url).get();
            } catch (IOException e) {
                sendMessage(chatId, messages.getString("enter_word"));
                log.warn("Error occurred while attempting this command: ", e);
            }
            Elements elements = doc.body().getElementsByClass("example");
            for (Element element : elements) {
                sendMessage(chatId, element.getElementsByClass("trg ltr").text()
                        + "\n" + element.getElementsByClass("src ltr").text());
            }
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

    private void chooseWayToGetWeather(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText("""
                Выберите способ получения погоды:
                1. Получить погоду по вашему местоположению;\s
                2. Получить погоду по выбранному населённому пункту""");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        modifyKeyboardSettings(keyboardMarkup);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        KeyboardRow row2 = new KeyboardRow();

        KeyboardButton locationWeatherButton = new KeyboardButton();
        locationWeatherButton.setText("1");
        locationWeatherButton.setRequestLocation(true);

        KeyboardButton getRegionsButton = new KeyboardButton();
        getRegionsButton.setText("2");

        row1.add(locationWeatherButton);
        row2.add(getRegionsButton);

        keyboardRows.add(row1);
        keyboardRows.add(row2);

        keyboardMarkup.setKeyboard(keyboardRows);
        sendMessage.setReplyMarkup(keyboardMarkup);
        tryToExecuteMessage(sendMessage);
    }

    private String getUrl(@NotNull Map<String, String> elements, String callbackData) {
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
        sendMessage.setText(messages.getString("region_list"));
        Document doc = connectToUrl("https://world-weather.ru/pogoda/russia");
        Elements links = doc.select("a[href]");

        if (state == BotStateEnum.SHOW_REGION_LIST) {
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            int i = 0;
            for (Element link : links) {
                if (link.attr("href").contains("/pogoda")
                        && !link.text().equals("Весь мир")) {
                    regions.put(link.text(), link.attr("href"));
                    inlineKeyboardCreate(rowsInline, ++i, link);
                }
            }
            putGeneralButtons(rowsInline);
            markupInline.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(markupInline);
            tryToExecuteMessage(sendMessage);
        }
    }

    private void getSettlement(Long chatId) {
        if (state == BotStateEnum.SHOW_SETTLEMENT_LIST) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(messages.getString("settlement_list"));
            Document doc = extractCallbackDataUrlFromElements(regions, region);

            Elements links2 = doc.select("a[href]");
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            int i = 0;

            for (Element link : links2) {
                if (link.attr("href").contains("/pogoda")
                        && !link.text().equals("Весь мир")
                        && !link.text().equals("Россия")) {
                    settlements.put(link.text(), "http:" + link.attr("href"));
                    inlineKeyboardCreate(rowsInline, ++i, link);
                }
            }
            putGeneralButtons(rowsInline);
            markupInline.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(markupInline);
            tryToExecuteMessage(sendMessage);
        }
    }

    private void inlineKeyboardCreate(List<List<InlineKeyboardButton>> rowsInline, int i, Element link) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText(i + ". " + link.text());
        inlineKeyboardButton.setCallbackData(link.text());
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        rowInLine.add(inlineKeyboardButton);
        rowsInline.add(rowInLine);
    }

    private void putGeneralButtons(List<List<InlineKeyboardButton>> rowsInline) {
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(EmojiParser.parseToUnicode(":arrow_left:"));
        backButton.setCallbackData("BACK");
        InlineKeyboardButton forwardButton = new InlineKeyboardButton();
        forwardButton.setText(EmojiParser.parseToUnicode(":arrow_right:"));
        forwardButton.setCallbackData("FORWARD");
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        rowInLine.add(backButton);
        rowInLine.add(forwardButton);
        rowsInline.add(rowInLine);
    }

    private void getWeatherFromSettlement(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
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
        sb.append(arr[7].split("\"")[3]).append(arr[7].split("\"")[4].replace("></em><b>", ": ").replace("</b></p>", "")).append("\nТемпература сейчас:").append(arr[9].replace("<span>", "").replace("</span>", "\n").replaceFirst(" ", ""));

        String[] arr2 = str2.split("\n");
        sb.append(arr2[1].split("\"")[1]).append("\n").append("Ветер: ").append(arr2[3].split("\"")[1]).append(", ").append(arr2[4].split("\"")[1]);

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

        KeyboardButton weatherButton = new KeyboardButton(EmojiParser.parseToUnicode(messages.getString("weather_button") + ":thunder_cloud_rain:"));
        row.add(weatherButton);

        KeyboardButton sentencesButton = new KeyboardButton(EmojiParser.parseToUnicode(messages.getString("sentences") + ":abc:"));
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

    private void tryToExecuteMessage(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.warn("Error occurred while attempting this command: ", e);
        }
    }
}