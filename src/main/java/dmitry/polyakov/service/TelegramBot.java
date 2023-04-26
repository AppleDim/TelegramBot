package dmitry.polyakov.service;

import com.vdurmont.emoji.EmojiParser;
import dmitry.polyakov.components.LanguageLocalisation;
import dmitry.polyakov.components.Menu;
import dmitry.polyakov.config.BotConfig;
import dmitry.polyakov.constants.BotStateEnum;
import dmitry.polyakov.model.*;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.helper.ValidationException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
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
import static dmitry.polyakov.components.LanguageLocalisation.messages;

/**
 * @author Dmitry Polyakov
 * @created 10.01.2023 23:47
 */
@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final BotConfig config;
    private UserRepository userRepository;
    private ScheduleRepository scheduleRepository;

    @Autowired
    public void setScheduleRepository(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

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
            onUpdateLocationReceived(update);
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            executeBotTypingProcess(chatId);
            User user = getUser(chatId);

            if (messageText.equals(START)) {
                handleStartCommand(update, chatId);

            } else if (messageText.equals(HELP)) {
                handleHelpCommand(update, chatId);

            } else if (messageText.equals(REGISTER)) {
                handleRegisterCommand(update, chatId);

            } else if (messageText.equals(DELETE)) {
                handleDeleteCommand(update, chatId);

            } else if (messageText.equals(LANGUAGE)) {
                handleLanguageCommand(update, chatId, user);
                
            } else if (messageText.equals(DICTIONARY)
                    || messageText.equals(EmojiParser.parseToUnicode(messages
                    .getString("dictionary") + ":gb:" + ":abc:" + ":ru:"))) {
                handleDictionaryCommand(update, chatId, user);

            } else if (messageText.equals(WEATHER)
                    || messageText.equals(EmojiParser.parseToUnicode(messages
                    .getString("weather_button") + ":thunder_cloud_rain:"))) {
                handleWeatherCommand(update, chatId, user);

            } else if (messageText.equals(LanguageLocalisation.englishLang)
                    || messageText.equals(LanguageLocalisation.russianLang)) {
                handleBotLanguageChange(update, user);

            } else if (messageText.equals(MY_DATA)) {
                handleMyDataCommand(update, chatId);

            } else if (isUserMatchedByIdAndBotState(chatId, SHOW_WEATHER_GETTER_WAY)
                    && messageText.equals("1. Погода по местоположению")) {
                handleWeatherFromStoredLocationRequest(update, chatId, user);

            } else if (isUserMatchedByIdAndBotState(chatId, SHOW_WEATHER_GETTER_WAY)
                    && messageText.equals("2. Список регионов")) {
                handleWeatherFromSettlementRequest(update, chatId, user);

            } else if (isUserMatchedByIdAndBotState(chatId, ASK_PHRASE)) {
                handlePhraseRequest(update, chatId, user);

            }  else if (messageText.equals(EmojiParser.parseToUnicode("1. Russian meanings" + ":clipboard:" + ":ru:"))
                && isUserMatchedByIdAndBotState(chatId, ASK_DICTIONARY_OPTION)) {
                handleRusMeaningsRequest(update, chatId, user);

            } else if (messageText.equals(EmojiParser.parseToUnicode("2. English meanings" + ":clipboard:" + ":gb:"))
                    && isUserMatchedByIdAndBotState(chatId, ASK_DICTIONARY_OPTION)) {
                handleEnglishMeaningsRequest(update, chatId, user);

            } else if (messageText.equals(EmojiParser.parseToUnicode("3. Russian-english sentences" + ":gb:" + ":ru:"))
                    && isUserMatchedByIdAndBotState(chatId, ASK_DICTIONARY_OPTION)) {
                handleRusEngSentencesRequest(update, chatId, user);

            } else if (messageText.equals(EmojiParser.parseToUnicode("4. English sentences" + ":abc:" + ":gb:"))
                    && isUserMatchedByIdAndBotState(chatId, ASK_DICTIONARY_OPTION)) {
                handleEngSentencesRequest(update, chatId, user);

            } else {
                if (isUserMatchedByIdAndBotState(chatId, DEFAULT_STATE)) {
                    handleNoCommandFoundRequest(update, chatId);
                }
            }

        } else if (update.hasCallbackQuery()) {
            String callBackData = update.getCallbackQuery().getData();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            User user = getUser(chatId);
            executeBotTypingProcess(chatId);
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
                    if (user.getChatId() == chatId) {
                        if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST) && user.line != 0) {
                            user.line = user.line - 10;
                            deleteMessage(messageId, chatId);
                            --user.page;
                            userRepository.save(user);
                            getRegion(chatId);

                        } else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST) && user.line != 0) {
                            user.line = user.line - 10;
                            deleteMessage(messageId, chatId);
                            --user.page;
                            userRepository.save(user);
                            getSettlement(chatId);

                        } else if (isUserMatchedByIdAndBotState(chatId, SHOW_WEATHER_IN_SETTLEMENT) && user.page > 0) {
                            deleteMessage(messageId, chatId);
                            --user.page;
                            userRepository.save(user);

                        } else {
                            user.line = 0;
                            userRepository.save(user);
                            deleteMessage(messageId, chatId);
                            if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) getRegion(chatId);
                            else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST))
                                getSettlement(chatId);
                        }
                    }
                }

                case "CANCEL_BUTTON" -> {
                    user.setUserBotState(DEFAULT_STATE);
                    deleteMessage(messageId, chatId);
                    userRepository.save(user);
                    sendMessage(chatId, messages.getString("click_button"));
                }

                case "FORWARD_BUTTON" -> {
                    if (user.getChatId() == chatId) {
                        if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)
                                && user.page < countLines(chatId) / 10 + 1) {
                            user.line = user.line + 10;
                            deleteMessage(messageId, chatId);
                            ++user.page;
                            userRepository.save(user);
                            getRegion(chatId);

                        } else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)
                                && user.page < countLines(chatId) / 10 + 1) {
                            user.line = user.line + 10;
                            deleteMessage(messageId, chatId);
                            ++user.page;
                            userRepository.save(user);
                            getSettlement(chatId);

                        } else if (isUserMatchedByIdAndBotState(chatId, SHOW_WEATHER_IN_SETTLEMENT) && user.page <= 10) {
                            deleteMessage(messageId, chatId);
                            ++user.page;
                            userRepository.save(user);

                        } else {
                            deleteMessage(messageId, chatId);
                            if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) getRegion(chatId);
                            else if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST))
                                getSettlement(chatId);
                        }
                    }
                }
            }

            if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) {
                saveRegionCallBackData(callBackData, messageId, chatId, user);
            }

            if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)) {
                saveSettlementCallBackData(callBackData, messageId, chatId, user);
            }

            if (isUserMatchedByIdAndBotState(chatId, SHOW_WEATHER_IN_SETTLEMENT)) {
                getWeather(chatId, user);
            }
        }
    }

    private void getWeather(long chatId, User user) {
        getWeatherInSettlement(chatId);
        if (user.page < 1) user.page = 1;
        if (user.page > 10) user.page = 10;
        log.info("@" + user.getUserName() + " received weather in "
                + user.settlement + " for day " + user.page);
    }

    private void saveSettlementCallBackData(String callBackData, int messageId, long chatId, User user) {
        if (user.getChatId() == chatId) {
            for (Map.Entry<String, String> entry : user.settlements.entrySet()) {
                if (entry.getKey().equals(callBackData)) {
                    user.settlement = callBackData;
                    executeEditMessage(messageId, chatId, messages.getString("settlement_chosen")
                            + callBackData);
                    user.setUserBotState(SHOW_WEATHER_IN_SETTLEMENT);
                    user.page = 1;
                    user.line = 0;
                    userRepository.save(user);
                    break;
                }
            }
        }
    }

    private void saveRegionCallBackData(String callBackData, int messageId, long chatId, User user) {
        if (user.getChatId() == chatId) {
            for (Map.Entry<String, String> entry : user.regions.entrySet()) {
                if (entry.getKey().equals(callBackData)) {
                    executeEditMessage(messageId, chatId, messages.getString("region_chosen") + " "
                            + callBackData);
                    user.region = callBackData;
                    user.setUserBotState(SHOW_SETTLEMENT_LIST);
                    user.page = 1;
                    user.line = 0;
                    userRepository.save(user);
                    break;
                }
            }
        }
        getSettlement(chatId);
    }

    private void onUpdateLocationReceived(Update update) {
        long chatId = update.getMessage().getChatId();
        User user = getUser(chatId);
        Location location = update.getMessage().getLocation();
        double longitude = location.getLongitude();
        double latitude = location.getLatitude();
        if (userRepository.findById(chatId).isPresent()) {
            user.setLocationCoordinates(String.format("%s, %s", longitude, latitude));
            userRepository.save(user);
            log.info("User's location updated: " + user);
        }
        getWeatherFromLocation(chatId, longitude, latitude);
        user.setUserBotState(DEFAULT_STATE);
        userRepository.save(user);
        log.info("User @" + update.getMessage().getChat().getUserName() + " sent his location");
    }

    private void handleStartCommand(Update update, long chatId) {
        registerUser(update.getMessage());
        startCommandReceived(chatId, update.getMessage().getChat().getUserName());
        sendMessage(chatId, messages.getString("click_button"));
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleHelpCommand(Update update, long chatId) {
        sendMessage(chatId, messages.getString("help_text"));
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleRegisterCommand(Update update, long chatId) {
        confirmScheduledInfoReceipt(chatId);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleDeleteCommand(Update update, long chatId) {
        deleteCommandReceived(chatId);
        log.info( "User @" + update.getMessage().getChat().getUserName() + "has been deleted.");
    }

    private void handleLanguageCommand(Update update, long chatId, User user) {
        changeLanguageKeyboardDisplay(chatId);
        user.setUserBotState(SHOW_LANGUAGES);
        userRepository.save(user);
        log.info(update.getMessage().getText() + "  executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleDictionaryCommand(Update update, long chatId, User user) {
        user.setUserBotState(ASK_PHRASE);
        userRepository.save(user);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(messages.getString("enter_word"));
        tryToExecuteMessage(sendMessage);
        log.info(update.getMessage().getText() + "  executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleWeatherCommand(Update update, long chatId, User user) {
        if (user.getChatId() == chatId) {
            user.line = 0;
            user.page = 1;
        }
        displayWeatherMenu(chatId);
        user.setUserBotState(SHOW_WEATHER_GETTER_WAY);
        userRepository.save(user);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleBotLanguageChange(Update update, User user) {
        languageChange(update);
        user.setUserBotState(DEFAULT_STATE);
        userRepository.save(user);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleMyDataCommand(Update update, long chatId) {
        String info = myDataCommandReceived(chatId);
        sendMessage(chatId, info);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleWeatherFromStoredLocationRequest(Update update, long chatId, User user) {
        if (userRepository.findById(chatId).isPresent()) {
            String coordinates = user.getLocationCoordinates();
            String[] cord = coordinates.split(", ");
            double longitude = Double.parseDouble(cord[0]);
            double latitude = Double.parseDouble(cord[1]);
            getWeatherFromLocation(chatId, longitude, latitude);
        }
        user.setUserBotState(DEFAULT_STATE);
        userRepository.save(user);
        log.info("User @" + update.getMessage().getChat().getUserName() + " sent his location");
    }

    private void handleWeatherFromSettlementRequest(Update update, long chatId, User user) {
        user.setUserBotState(SHOW_REGION_LIST);
        userRepository.save(user);
        getRegion(chatId);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handlePhraseRequest(Update update, long chatId, User user) {
        if (user.getChatId() == chatId) {
            user.words = update.getMessage().getText();
        }
        user.setUserBotState(ASK_DICTIONARY_OPTION);
        userRepository.save(user);
        displayDictionaryMenu(chatId);
    }

    private void handleRusMeaningsRequest(Update update, long chatId, User user) {
        displayRusWords(chatId);
        if (user.getChatId() == chatId) {
            user.words = null;
        }
        user.setUserBotState(DEFAULT_STATE);
        userRepository.save(user);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }
    private void handleEnglishMeaningsRequest(Update update, long chatId, User user) {
        displayMeaningsOfWord(chatId);
        if (user.getChatId() == chatId) {
            user.words = null;
        }
        user.setUserBotState(DEFAULT_STATE);
        userRepository.save(user);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleRusEngSentencesRequest(Update update, long chatId, User user) {
        displayRusEngSentences(chatId);
        if (user.getChatId() == chatId) {
            user.words = null;
        }
        user.setUserBotState(DEFAULT_STATE);
        userRepository.save(user);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleEngSentencesRequest(Update update, long chatId, User user) {
        displayExamplesWithSentences(chatId);
        if (user.getChatId() == chatId) {
            user.words = null;
        }
        user.setUserBotState(DEFAULT_STATE);
        userRepository.save(user);
        log.info(update.getMessage().getText() + " executed by @"
                + update.getMessage().getChat().getUserName());
    }

    private void handleNoCommandFoundRequest(Update update, long chatId) {
        sendMessage(chatId, messages.getString("non-existing_command"));
        log.info("User @" + update.getMessage().getChat().getUserName()
                + " tried to execute non-existing command "
                + update.getMessage().getText() + ". ");
    }


    public void deleteMessage(int messageId, long chatId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setMessageId(messageId);
        deleteMessage.setChatId(String.valueOf(chatId));
        tryToExecuteMessage(deleteMessage);
    }

    public void displayDictionaryMenu(long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText("""
                Что хотите увидеть:
                1. Значения слова на русском\s
                2. Объяснения слова на английском\s
                3. Предложения на английском с переводом\s
                4. Предложения на английском""");

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

    public void displayMeaningsOfWord(long chatId) {
        User user = getUser(chatId);
        if (user.getChatId() == chatId) {
            try {
                Document doc = Jsoup.connect("https://dictionary.cambridge.org/dictionary/english/" + user.words).get();
                Elements elements = doc.getElementsByClass("sense-body dsense_b");
                for (Element element : elements) {
                    sendMessage(chatId, element.getElementsByClass("def ddef_d db")
                            .text().replace(":", "\n"));
                }
            } catch (IOException e) {
                sendMessage(chatId, "Word or phrase hasn't been found.");
                user.words = null;
                user.setUserBotState(DEFAULT_STATE);
                userRepository.save(user);
                log.warn("Error occurred while attempting this command: ", e);
            }
        }
    }

    public void displayRusWords(long chatId) {
        User user = getUser(chatId);
        if (user.getChatId() == chatId) {
            try {
                String url = "https://context.reverso.net/перевод/русский-английский/" + user.words;
                Document doc = Jsoup.connect(url).get();
                Elements elements = doc.body().getElementsByClass("display-term");
                StringBuilder sb = new StringBuilder("Перевод слова: \n");
                for (int i = 0; i < elements.size() - 1; i++) {
                    sb.append(elements.get(i).text()).append(", ");
                }
                sendMessage(chatId, sb.append(elements.get(elements.size() - 1).text()).append(".").toString());
            } catch (IOException e) {
                sendMessage(chatId, "Word or phrase hasn't been found.");
                user.words = null;
                user.setUserBotState(DEFAULT_STATE);
                userRepository.save(user);
                log.warn("Error occurred while attempting this command: ", e);
            }
        }
    }

    public void displayRusEngSentences(long chatId) {
        User user = getUser(chatId);
        if (user.getChatId() == chatId) {
            try {
                String url = "https://context.reverso.net/translation/english-russian/" + user.words;
                Document doc = Jsoup.connect(url).get();
                Elements elements = doc.body().getElementsByClass("example");
                for (Element element : elements) {
                    sendMessage(chatId, element.getElementsByClass("trg ltr").text()
                            + "\n" + element.getElementsByClass("src ltr").text());
                }
            } catch (IOException e) {
                sendMessage(chatId, "Word or phrase hasn't been found.");
                user.words = null;
                user.setUserBotState(DEFAULT_STATE);
                userRepository.save(user);
                log.warn("Error occurred while attempting this command: ", e);
            }
        }
    }

    public void displayExamplesWithSentences(long chatId) {
        User user = getUser(chatId);
        if (user.getChatId() == chatId) {
            try {
                Document doc = Jsoup.connect("https://dictionary.cambridge.org/dictionary/english/" + user.words).get();
                Elements elements3 = doc.body().getElementsByClass("lbb lb-cm lpt-10");
                for (Element element : elements3) {
                    sendMessage(chatId, element.getElementsByClass("deg").text());
                }
            } catch (IOException e) {
                sendMessage(chatId, "Word or phrase hasn't been found.");
                user.words = null;
                user.setUserBotState(DEFAULT_STATE);
                userRepository.save(user);
                log.warn("Error occurred while attempting this command: ", e);
            }
        }
    }

    public void displayWeatherMenu(long chatId) {
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

    public String getUrl(Map<String, String> elements, String callbackData) {
        String elementUrl = null;
        for (Map.Entry<String, String> entry : elements.entrySet()) {
            if (entry.getKey().equals(callbackData))
                elementUrl = entry.getValue();
        }
        return elementUrl;
    }

    public Document extractCallbackDataUrlFromElements(Map<String, String> elements, String callbackData, long chatId) {
        User user = getUser(chatId);
        Document doc = null;
        try {
            doc = Jsoup.connect(getUrl(elements, callbackData)).get();
        } catch (IOException | ValidationException | NullPointerException e) {
            log.warn("Error occurred while attempting this command: ", e);
            user.setUserBotState(DEFAULT_STATE);
            userRepository.save(user);
        }

        return doc;
    }

    public Document connectToUrl(String url, long chatId) {
        User user = getUser(chatId);
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException | ValidationException | NullPointerException e) {
            log.warn("Error occurred while attempting this command: ", e);
            user.setUserBotState(DEFAULT_STATE);
            userRepository.save(user);
        }

        return doc;
    }

    public void getWeatherFromLocation(long chatId, double longitude, double latitude) {
        try {
            String url = "https://yandex.ru/pogoda/" + String.format(
                    "?lat=%f&lon=%f&via=hnav",
                    latitude, longitude).replace(",", ".");
            Document doc = Jsoup.connect(url).userAgent("Mozilla").ignoreContentType(true)
                    .ignoreHttpErrors(true).get();
            try {
                sendMessage(chatId, Objects.requireNonNull(doc.getElementById("main_title")).text());
            } catch(NullPointerException e) {
                sendMessage(chatId, "Функция получения погоды по геолокации в данный момент не работает.");
            }
            Elements elements = doc.body().getElementsByClass("a11y-hidden");
            Elements elements1 = doc.getElementsByClass("term__value");
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (Element element : elements1) {
                i++;
                switch(i) {
                    case 1 -> sb.append("Температура ").append(element.text()).append(" °C").append("\n");
                    case 2 -> sb.append("Ощущается как ").append(element.text()).append(" °C").append("\n");
                    case 3 -> sb.append("Скорость ветра ").append(element.text()).append("\n");
                    case 4 -> sb.append("Влажность ").append(element.text()).append("\n");
                    case 5 -> sb.append("Давление ").append(element.text()).append("\n");
                }
            }
            int j = 0;
            for (Element element : elements) {
                if (j < 1 || j > 4 && j < elements.size() - 3) {
                    String str = element.text()
                            .replace("В ", EmojiParser.parseToUnicode(":thermometer:"))
                            .replace(" часа", ":00")
                            .replace(" часов", ":00")
                            .replace(" час", ":00");
                    sb.append(str).append("\n");
                }
                j++;
            }
            sendMessage(chatId, sb.toString());

        } catch (IOException e) {
            log.warn("Error occurred while attempting this command: ", e);
        }
    }

    public int countLines(long chatId) {
        User user = getUser(chatId);
        if (user.getChatId() == chatId) {
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
                Document doc = extractCallbackDataUrlFromElements(user.regions, user.region, chatId);
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
        }
        return 0;
    }

    public void getRegion(long chatId) {
        User user = getUser(chatId);
        if (user.getChatId() == chatId) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(messages.getString("region_list") + user.page + "/" + (countLines(chatId) / 10 + 1) + ": ");

            Document doc = connectToUrl("https://world-weather.ru/pogoda/russia", chatId);
            Elements links = doc.select("a[href]");

            if (isUserMatchedByIdAndBotState(chatId, SHOW_REGION_LIST)) {
                user.regions = new TreeMap<>();
                InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
                int j = 0;
                for (int i = user.line; j < 10; i++) {
                    try {
                        if (links.get(i).attr("href").contains("/pogoda")
                                && !links.get(i).text().equals("Весь мир")) {
                            j++;
                            user.regions.put(links.get(i).text(), links.get(i).attr("href"));
                        }
                    } catch (IndexOutOfBoundsException e) {
                        break;
                    }
                }
                int i = 0;
                for (Map.Entry<String, String> regionEntry : user.regions.entrySet()) {
                    inlineKeyboardCreate(rowsInline, ++i, regionEntry.getKey());
                }
                putGeneralButtons(rowsInline);
                markupInline.setKeyboard(rowsInline);
                sendMessage.setReplyMarkup(markupInline);
                tryToExecuteMessage(sendMessage);

                userRepository.save(user);
            }
        }
    }

    public void getSettlement(long chatId) {
        User user = getUser(chatId);
        if (user.getChatId() == chatId) {
            if (isUserMatchedByIdAndBotState(chatId, SHOW_SETTLEMENT_LIST)) {
                user.settlements = new TreeMap<>();
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(String.valueOf(chatId));
                sendMessage.setText(messages.getString("settlement_list") + user.page + "/" + (countLines(chatId) / 10 + 1) + ": ");
                Document doc = extractCallbackDataUrlFromElements(user.regions, user.region, chatId);

                Elements links = doc.select("a[href]");
                InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

                int j = 0;
                for (int i = user.line; j < 10; i++) {
                    try {
                        if (links.get(i).attr("href").contains("/pogoda")
                                && !links.get(i).text().equals("Весь мир")
                                && !links.get(i).text().equals("Россия")) {
                            j++;
                            user.settlements.put(links.get(i).text(), "http:" + links.get(i).attr("href") + "10days");
                        }
                    } catch (IndexOutOfBoundsException e) {
                        break;
                    }
                }
                int i = 0;
                for (Map.Entry<String, String> settlementEntry : user.settlements.entrySet()) {
                    inlineKeyboardCreate(rowsInline, ++i, settlementEntry.getKey());
                }
                putGeneralButtons(rowsInline);
                markupInline.setKeyboard(rowsInline);
                sendMessage.setReplyMarkup(markupInline);
                tryToExecuteMessage(sendMessage);

                userRepository.save(user);
            }
        }
    }

    public void inlineKeyboardCreate(List<List<InlineKeyboardButton>> rowsInline, int i, String linkText) {
        InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton();
        inlineKeyboardButton.setText(i + ". " + linkText);
        inlineKeyboardButton.setCallbackData(linkText);
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        rowInLine.add(inlineKeyboardButton);
        rowsInline.add(rowInLine);
    }

    public void putGeneralButtons(List<List<InlineKeyboardButton>> rowsInline) {
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

    public void getWeatherInSettlement(long chatId) {
        User user = getUser(chatId);
        if (user.getChatId() == chatId) {
            Document doc = extractCallbackDataUrlFromElements(user.settlements, user.settlement, chatId);
            Elements elements = doc.body().getElementsByClass("weather-short");
            Elements sunRisesElems = doc.body().getElementsByClass("sun-box");
            String elements1 = doc.getElementsByClass("weather-temperature").outerHtml();
            String[] weather = elements1.replace("<td class=\"weather-temperature\">", "").split("\n");
            String elements2 = doc.getElementsByClass("weather-wind").outerHtml();
            String[] wind = elements2.split("\n");
            int i = 0;
            int j = 0;
            int m = 0;
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            user.days = new ArrayList<>();
            for (Element element : elements) {
                user.days.add(EmojiParser.parseToUnicode(":calendar:")
                        + (element.getElementsByClass("dates short-d").hasText()
                        ? ("*" + element.getElementsByClass("dates short-d").text() + "*" + " \n")
                        : ("*" + element.getElementsByClass("dates short-d red").text()) + "*" + " \n")
                        + "---------------------------------" + "\n"
                        + parseWeatherStrings(element, weather, 1 + 2 * j++, "night", wind, m++) + "\n"
                        + parseWeatherStrings(element, weather, 1 + 2 * j++, "morning", wind, m++) + "\n"
                        + parseWeatherStrings(element, weather, 1 + 2 * j++, "day", wind, m++) + "\n"
                        + parseWeatherStrings(element, weather, 1 + 2 * j++, "evening", wind, m++) + "\n"
                        + EmojiParser.parseToUnicode(":sunrise:")
                        + "Восход: " + sunRisesElems.get(i).text().split(" ")[0] + "\n"
                        + EmojiParser.parseToUnicode(":sunrise_over_mountains:")
                        + " Закат: " + sunRisesElems.get(i++).text().split(" ")[1]);
            }
            sendMessage.setParseMode(ParseMode.MARKDOWN);
            if (user.page < 1) user.page = 1;
            if (user.page > 10) user.page = 10;
            sendMessage.setText(user.days.get(user.page - 1));
            InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            putGeneralButtons(rowsInline);
            markupInline.setKeyboard(rowsInline);
            sendMessage.setReplyMarkup(markupInline);
            tryToExecuteMessage(sendMessage);

            userRepository.save(user);
        }
    }

    public String parseWeatherStrings(Element element, String[] weather, int j, String elementByClass, String[] wind, int m) {
        String[] params = element.getElementsByClass(elementByClass).text().split(" ");
        return addEmojiToDaytime(params[0]) + "\n"
                + addEmojiToWeatherString(weather, j) + "\n"
                + EmojiParser.parseToUnicode(":thermometer:") + "_Температура:_ " + params[1] + "С" + "\n"
                + "☂_Вероятность осадков:_ " + params[3] + "\n"
                + EmojiParser.parseToUnicode(":compass: ") + "_Давление:_ " + params[4] + " мм.рт.ст.\n"
                + EmojiParser.parseToUnicode(":cyclone:") + "_Скорость ветра:_ " + params[5] + " м/c\n"
                + EmojiParser.parseToUnicode(":wavy_dash:") + "_Направление ветра:_ " + replaceWindDirectionWithEmoji(wind, m) + "\n"
                + EmojiParser.parseToUnicode(":droplet:") + "_Влажность воздуха:_ " + params[6] + "\n";
    }

    public String addEmojiToDaytime(String str) {
        return switch (str) {
            case "Ночь" -> EmojiParser.parseToUnicode(":new_moon_with_face: *Ночь*");
            case "Утро" -> EmojiParser.parseToUnicode(":sunrise: *Утро*");
            case "День" -> EmojiParser.parseToUnicode(":sun_with_face: *День*");
            case "Вечер" -> EmojiParser.parseToUnicode(":night_with_stars: *Вечер*");
            default -> "";
        };
    }

    public String addEmojiToWeatherString(String[] weather, int j) {
        String str = "";
        switch (weather[j].split("\"")[3]) {
            case "Преимущественно облачно" ->
                    str = EmojiParser.parseToUnicode(":white_sun_behind_cloud: _Преимущественно облачно_");
            case "Частично облачно" -> str = EmojiParser.parseToUnicode(":white_sun_small_cloud: _Частично облачно_");
            case "Незначительная облачность" ->
                    str = EmojiParser.parseToUnicode(":white_sun_small_cloud: _Незначительная облачность_");
            case "Пасмурно" -> str = EmojiParser.parseToUnicode(":cloud: _Пасмурно_");
            case "Ясно" -> str = EmojiParser.parseToUnicode(":high_brightness: _Ясно_");
            case "Кратковременные осадки" -> str = EmojiParser.parseToUnicode(":cloud_rain: _Кратковременные осадки_");
            case "Слабый дождь" -> str = EmojiParser.parseToUnicode(":white_sun_behind_cloud_rain: _Слабый дождь_");
            case "Местами сильный дождь" -> str = EmojiParser.parseToUnicode(":white_sun_behind_cloud_rain: _Дождь_");
            case "Облачно и слабый снег" -> str = EmojiParser.parseToUnicode(":cloud_snow: _Слабый снег_");
            case "Сильный снег" -> str = EmojiParser.parseToUnicode(":cloud_snow: _Сильный снегопад_");
            case "Снег" -> str = EmojiParser.parseToUnicode(":cloud_snow: _Снег_");
            case "Сильный дождь" -> str = EmojiParser.parseToUnicode(":cloud_rain: _Ливень_");
            case "Сильный дождь, гроза" -> str = EmojiParser.parseToUnicode(":thunder_cloud_rain: _Ливень с грозой_");
            case "Дождь с грозой" -> str = EmojiParser.parseToUnicode(":thunder_cloud_rain: _Дождь с грозой_");
            case "Дождь" -> str = EmojiParser.parseToUnicode(":cloud_rain: _Дождь_");
        }
        return str;
    }

    public String replaceWindDirectionWithEmoji(String[] wind, int m) {
        String str = "";
        switch (wind[m].split("\"")[5]) {
            case "восточный" -> str = EmojiParser.parseToUnicode(":arrow_right:");
            case "юго-восточный" -> str = EmojiParser.parseToUnicode(":arrow_lower_right:");
            case "южный" -> str = EmojiParser.parseToUnicode(":arrow_down:");
            case "юго-западный" -> str = EmojiParser.parseToUnicode(":arrow_lower_left:");
            case "западный" -> str = EmojiParser.parseToUnicode(":arrow_left:");
            case "северо-западный" -> str = EmojiParser.parseToUnicode(":arrow_upper_left:");
            case "северный" -> str = EmojiParser.parseToUnicode(":arrow_up:");
            case "северо-восточный" -> str = EmojiParser.parseToUnicode(":arrow_upper_right:");
        }
        return str;
    }

    public void executeEditMessage(int messageId, long chatId, String text) {
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

    public void confirmScheduledInfoReceipt(long chatId) {
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
        User user = getUser(chatId);
        return user.getUserName() + "\n"
                + user.getFirstName() + "\n"
                + user.getRegisteredTime() + "\n"
                + user.getLocationCoordinates();
    }

    public void changeLanguageKeyboardDisplay(long chatId) {
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


    public void modifyKeyboardSettings(ReplyKeyboardMarkup keyboardMarkup) {
        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);
    }

    public void languageChange(Update update) {
        long chatId = update.getMessage().getChatId();
        User user = getUser(chatId);
        if (isUserMatchedByIdAndBotState(chatId, SHOW_LANGUAGES)) {
            String messageText = update.getMessage().getText();

            if (messageText.equals(LanguageLocalisation.englishLang)) {
                messages = ResourceBundle.getBundle("messages", Locale.of("en"));

            } else if (messageText.equals(LanguageLocalisation.russianLang)) {
                messages = ResourceBundle.getBundle("messages", Locale.of("ru"));
            }

            sendMessage(update.getUpdateId(), messages.getString("swap_language"));

            user.setUserBotState(DEFAULT_STATE);
            userRepository.save(user);

        } else sendMessage(update.getUpdateId(), messages.
                getString("non-existing_command"));
    }

    public void registerUser(Message msg) {
        if (userRepository.findById(msg.getChatId()).isEmpty()) {
            long chatId = msg.getChatId();
            Chat chat = msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setUserName(chat.getUserName());
            user.setRegisteredTime(new Timestamp(System.currentTimeMillis()));
            user.setUserBotState(DEFAULT_STATE);
            userRepository.save(user);
            log.info("User saved: " + user);
        }
    }

    public void deleteCommandReceived(long chatId) {
       if (userRepository.findById(chatId).isPresent()) {
           userRepository.deleteById(chatId);
       }
       sendMessage(chatId, "Вы успешно были удалены из базы данных.");
    }

    public void startCommandReceived(long chatId, String firstName) {
        User user = getUser(chatId);
        String greeting = messages.getString("greeting");
        String answer = EmojiParser.parseToUnicode(greeting + firstName + " :wave:");
        user.setUserBotState(DEFAULT_STATE);
        sendMessage(chatId, answer);

        userRepository.save(user);
    }

    public void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);
        sendMessage.setParseMode(ParseMode.MARKDOWN);

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

        keyboardRows.add(row);

        keyboardMarkup.setKeyboard(keyboardRows);

        sendMessage.setReplyMarkup(keyboardMarkup);

        tryToExecuteMessage(sendMessage);
    }

    public void executeBotTypingProcess(long chatId) {
        try {
            SendChatAction sendChatAction = new SendChatAction();
            sendChatAction.setAction(ActionType.TYPING);
            sendChatAction.setChatId(String.valueOf(chatId));
            tryToExecuteMessage(sendChatAction);
            Thread.sleep(500);
        } catch (InterruptedException e) {
            log.warn("Error occurred while creating pause between message sending: ", e);
        }
    }

    public void tryToExecuteMessage(BotApiMethod<?> message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn("Error occurred while attempting this command: ", e);
        }
    }

    private boolean isUserMatchedByIdAndBotState(long chatId, BotStateEnum botState) {
        User user = getUser(chatId);
        return user.getUserBotState() == botState;
    }

    private Optional<User> findUser(long chatId) {
        return userRepository.findById(chatId);
    }

    public User getUser(long chatId) {
        Optional<User> optionalUser = findUser(chatId);
        if (optionalUser.isPresent()) {
            return optionalUser.get();
        } else {
            log.warn("User not found: " + chatId);
            return null;
        }
    }


    @Scheduled(cron = "0 0 8 * * *")
    public void sendScheduledMessages() {
        var scheduleMessages = scheduleRepository.findAll();
        Iterable<User> users = userRepository.findAll();

        for (Schedule schedule : scheduleMessages) {
            for (User user : users) {
                sendMessage(user.getChatId(), schedule.getMessage());
            }
        }
    }
}